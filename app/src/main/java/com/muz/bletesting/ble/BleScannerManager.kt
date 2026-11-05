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

    // Boleh diubah secara langsung oleh SeekBar di MainActivity (-100 hingga -40 dBm)
    var rssiThreshold: Int = -75

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val record = result?.scanRecord ?: return
            val device = result.device ?: return
            val devAddress = device.address ?: return

            // 1. Tapis signal lemah mengikut had slider masa nyata
            if (result.rssi < rssiThreshold) {
                return
            }

            val manData = record.manufacturerSpecificData
            val serviceDataMap = record.serviceData
            val serviceUuids = record.serviceUuids

            var mId = -1
            var mPayload = ByteArray(0)
            var sUuid: String? = null
            var sPayload = ByteArray(0)

            if (manData != null && manData.size() > 0) {
                mId = manData.keyAt(0)
                mPayload = manData.valueAt(0) ?: ByteArray(0)
            } else if (serviceDataMap != null && serviceDataMap.isNotEmpty()) {
                val entry = serviceDataMap.entries.first()
                sUuid = entry.key.toString()
                sPayload = entry.value ?: ByteArray(0)
            } else if (!serviceUuids.isNullOrEmpty()) {
                sUuid = serviceUuids[0].toString()
            } else {
                val rawBytes = record.bytes
                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    mPayload = rawBytes.take(24).toByteArray()
                }
            }

            // 2. Sekat peranti pengacau: Apple (76), Microsoft (6), Samsung (117)
            if (mId == 76 || mId == 6 || mId == 117) {
                return
            }

            // 3. Abaikan paket kosong tanpa data
            if (mId == -1 && sPayload.isEmpty() && sUuid == null && mPayload.isEmpty()) {
                return
            }

            val rawDeviceName = try {
                device.name
            } catch (e: SecurityException) {
                null
            }

            val finalPayload = if (sPayload.isNotEmpty()) sPayload else mPayload
            val finalName = BleParserUtils.tentukanNamaPeranti(record, rawDeviceName, mId, finalPayload, sUuid)

            val target = BleTarget(
                name = finalName,
                address = devAddress,
                manufacturerId = mId,
                manufacturerData = mPayload,
                serviceUuid = sUuid,
                serviceData = sPayload,
                rssi = result.rssi,
                lastSeen = System.currentTimeMillis()
            )

            onDeviceFound(target)
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
                // Elak ralat jika Bluetooth ditutup mendadak
            }
            isScanning = false
        }
    }
}
