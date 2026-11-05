package com.muz.bletesting.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.muz.bletesting.model.BleTarget

class BleAdvertiserManager(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val onSuccess: (AdvertiseSettings?) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onPulseStatusChange: ((isPulseActive: Boolean) -> Unit)? = null
) {
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    var isAdvertising: Boolean = false
        private set

    // Konfigurasi Mod Burst
    var isBurstMode: Boolean = false
    var burstOnSeconds: Int = 3
    var burstOffSeconds: Int = 2

    private val burstHandler = Handler(Looper.getMainLooper())
    private var isPulseRadioActive = false
    private var targetSemasa: BleTarget? = null

    private val burstCycleRunnable = object : Runnable {
        override fun run() {
            if (!isAdvertising || targetSemasa == null) return

            if (isPulseRadioActive) {
                // Fasa 1: Hentikan radio untuk tempoh rehat (OFF)
                hentikanRadioSahaja()
                isPulseRadioActive = false
                onPulseStatusChange?.invoke(false)
                burstHandler.postDelayed(this, burstOffSeconds * 1000L)
            } else {
                // Fasa 2: Hidupkan radio semula untuk tempoh pancaran (ON)
                mulaPancarRadioSahaja(targetSemasa!!)
                isPulseRadioActive = true
                onPulseStatusChange?.invoke(true)
                burstHandler.postDelayed(this, burstOnSeconds * 1000L)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            onSuccess(settingsInEffect)
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            if (!isBurstMode) {
                isAdvertising = false
            }
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

        targetSemasa = target
        isAdvertising = true

        if (isBurstMode) {
            // Mulakan fasa kitaran berkala (Pancar -> Rehat -> Pancar)
            isPulseRadioActive = true
            val berjaya = mulaPancarRadioSahaja(target)
            onPulseStatusChange?.invoke(true)
            burstHandler.postDelayed(burstCycleRunnable, burstOnSeconds * 1000L)
            return berjaya
        } else {
            // Mod pancaran biasa (berterusan)
            return mulaPancarRadioSahaja(target)
        }
    }

    @SuppressLint("MissingPermission")
    private fun mulaPancarRadioSahaja(target: BleTarget): Boolean {
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
                val payload = if (target.manufacturerData.size > 24) {
                    target.manufacturerData.copyOfRange(0, 24)
                } else {
                    target.manufacturerData
                }
                dataBuilder.addManufacturerData(target.manufacturerId, payload)
            } else if (target.serviceUuid != null) {
                val pUuid = ParcelUuid.fromString(target.serviceUuid)
                dataBuilder.addServiceUuid(pUuid)

                if (target.serviceData.isNotEmpty()) {
                    val sPayload = if (target.serviceData.size > 20) {
                        target.serviceData.copyOfRange(0, 20)
                    } else {
                        target.serviceData
                    }
                    dataBuilder.addServiceData(pUuid, sPayload)
                }
            } else {
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
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private fun hentikanRadioSahaja() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            // Elak ralat penutupan mendadak
        }
    }

    @SuppressLint("MissingPermission")
    fun hentiPancar() {
        burstHandler.removeCallbacks(burstCycleRunnable)
        hentikanRadioSahaja()
        isAdvertising = false
        isPulseRadioActive = false
        targetSemasa = null
        onPulseStatusChange?.invoke(false)
    }
}
