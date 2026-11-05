package com.muz.bletesting.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.ParcelUuid
import com.muz.bletesting.model.BleTarget

class BleAdvertiserManager(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val onSuccess: (AdvertiseSettings?) -> Unit,
    private val onFailure: (String) -> Unit
) {
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    var isAdvertising: Boolean = false
        private set

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            isAdvertising = true
            onSuccess(settingsInEffect)
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            isAdvertising = false
            val ralatDesc = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data melebihi had 31 bait BLE!"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Terlalu banyak pemancar aktif dalam sistem!"
                ADVERTISE_FAILED_ALREADY_STARTED -> "Pemancar sudah sedia aktif."
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Ralat pemacu Bluetooth dalaman telefon."
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Cip radio telefon tidak menyokong fungsi BLE Advertising."
                else -> "Ralat kod tidak diketahui: $errorCode"
            }
            onFailure("Gagal memancar klon!\nPunca: $ralatDesc")
        }
    }

    @SuppressLint("MissingPermission")
    fun mulaPancar(target: BleTarget): Boolean {
        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bleAdvertiser == null) {
            onFailure("Telefon ini tidak menyokong fungsi pemancar BLE (cip terhad).")
            return false
        }

        if (isAdvertising) {
            hentiPancar()
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)

        try {
            if (target.manufacturerId != -1 && target.manufacturerData.isNotEmpty()) {
                // 1. Pancar mengikut format Manufacturer Data (Apple, Samsung, Tuya, dll)
                val payload = if (target.manufacturerData.size > 24) {
                    target.manufacturerData.copyOfRange(0, 24)
                } else {
                    target.manufacturerData
                }
                dataBuilder.addManufacturerData(target.manufacturerId, payload)
            } else if (target.serviceUuid != null) {
                // 2. Pancar mengikut format Service Data / UUID (Generic BLE / Fast Pair / IoT)
                val pUuid = ParcelUuid.fromString(target.serviceUuid)
                dataBuilder.addServiceUuid(pUuid)

                if (target.serviceData.isNotEmpty()) {
                    // Dihadkan kepada 20 bait bagi mengelakkan limpahan had 31 bait Legacy BLE
                    val sPayload = if (target.serviceData.size > 20) {
                        target.serviceData.copyOfRange(0, 20)
                    } else {
                        target.serviceData
                    }
                    dataBuilder.addServiceData(pUuid, sPayload)
                }
            } else {
                // 3. Sandaran kecemasan jika tiada struktur khusus
                val payload = if (target.manufacturerData.size > 24) {
                    target.manufacturerData.copyOfRange(0, 24)
                } else {
                    target.manufacturerData
                }
                dataBuilder.addManufacturerData(0xFFFF, payload)
            }

            bleAdvertiser?.startAdvertising(settings, dataBuilder.build(), advertiseCallback)
            return true
        } catch (e: Exception) {
            onFailure("Ralat memancar: ${e.localizedMessage}")
            isAdvertising = false
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun hentiPancar() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            // Elak ralat jika perkhidmatan terputus
        }
        isAdvertising = false
    }
}
