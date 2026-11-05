package com.muz.bletesting.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
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

        val payload = if (target.manufacturerData.size > 24) {
            target.manufacturerData.copyOfRange(0, 24)
        } else {
            target.manufacturerData
        }

        val effectiveManId = if (target.manufacturerId != -1) target.manufacturerId else 0xFFFF

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(effectiveManId, payload)

        return try {
            bleAdvertiser?.startAdvertising(settings, dataBuilder.build(), advertiseCallback)
            true
        } catch (e: Exception) {
            onFailure("Ralat memancar: ${e.localizedMessage}")
            isAdvertising = false
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun hentiPancar() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            // Elak crash
        }
        isAdvertising = false
    }
}
