package com.muz.bletesting.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.utils.BleParserUtils

class BleScannerManager(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val onDeviceFound: (BleTarget) -> Unit,
    private val onError: (String) -> Unit
) {
    private var bleScanner: BluetoothLeScanner? = null
    var isScanning: Boolean = false
        private set

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val record = result?.scanRecord ?: return
            val device = result.device ?: return

            val devAddress = device.address ?: return
            val manData = record.manufacturerSpecificData

            var mId = -1
            var payload: ByteArray? = null

            if (manData != null && manData.size() > 0) {
                mId = manData.keyAt(0)
                payload = manData.valueAt(0)
            } else {
                val rawBytes = record.bytes
                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    payload = rawBytes.take(24).toByteArray()
                }
            }

            if (payload != null) {
                val rawDeviceName = try {
                    device.name
                } catch (e: SecurityException) {
                    null
                }

                val finalName = BleParserUtils.tentukanNamaPeranti(record, rawDeviceName, mId, payload)
                val target = BleTarget(finalName, devAddress, mId, payload, result.rssi)
                onDeviceFound(target)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            onError("Imbasan gagal dijalankan. Ralat kod: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun mulaImbas(): Boolean {
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            onError("Cip BLE Scanner tidak dijumpai atau Bluetooth belum aktif.")
            return false
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return try {
            bleScanner?.startScan(emptyList(), settings, scanCallback)
            isScanning = true
            true
        } catch (e: Exception) {
            onError("Ralat imbas: ${e.localizedMessage}")
            isScanning = false
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun hentiImbas() {
        if (isScanning) {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                // Elak crash jika radio dimatikan mengejut
            }
            isScanning = false
        }
    }
}
