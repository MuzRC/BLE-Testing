package com.muz.bletesting.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.muz.bletesting.utils.BleParserUtils
import java.util.UUID

class BleGattManager(private val context: Context) {

    private var bluetoothGatt: BluetoothGatt? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var isConnected: Boolean = false
        private set

    // UUID Standard Industri
    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    private val BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

    var onStatusChange: ((statusText: String, isConnected: Boolean) -> Unit)? = null
    var onServicesDiscovered: ((senaraiServis: List<String>, peratusBateri: Int?) -> Unit)? = null

    private var peratusBateriTerkini: Int? = null

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                mainHandler.post {
                    onStatusChange?.invoke("Berjaya disambung! Menyelongkar servis dalaman...", true)
                }
                // Mulakan selongkar servis
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                mainHandler.post {
                    onStatusChange?.invoke("Sambungan diputuskan.", false)
                }
                tutup()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val hasilServis = mutableListOf<String>()

                for (service in gatt.services) {
                    val sUuidShort = formatUuidRingkas(service.uuid.toString())
                    val sName = kenalPastiServis(sUuidShort)

                    val sb = StringBuilder()
                    sb.append("◆ Servis: $sName ($sUuidShort)\n")

                    for (char in service.characteristics) {
                        val cUuidShort = formatUuidRingkas(char.uuid.toString())
                        val cName = kenalPastiKarakteristik(cUuidShort)
                        val prop = huraikanSifat(char.properties)
                        sb.append("   └ C: $cName [$cUuidShort] ($prop)\n")
                    }
                    hasilServis.add(sb.toString().trimEnd())
                }

                // Semak jika ada Servis Bateri untuk terus dibaca
                val batteryChar = gatt.getService(BATTERY_SERVICE_UUID)?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                if (batteryChar != null) {
                    gatt.readCharacteristic(batteryChar)
                } else {
                    mainHandler.post {
                        onServicesDiscovered?.invoke(hasilServis, null)
                    }
                }
            } else {
                mainHandler.post {
                    onStatusChange?.invoke("Gagal menyelongkar servis. Kod: $status", true)
                }
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)

            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    val rawVal = characteristic.value
                    if (rawVal != null && rawVal.isNotEmpty()) {
                        peratusBateriTerkini = rawVal[0].toInt() and 0xFF
                    }
                }

                // Kumpul semula senarai servis bersama bacaan bateri
                if (gatt != null) {
                    val list = gatt.services.map { s ->
                        val sUuid = formatUuidRingkas(s.uuid.toString())
                        val sName = kenalPastiServis(sUuid)
                        val chars = s.characteristics.joinToString("\n") { c ->
                            val cUuid = formatUuidRingkas(c.uuid.toString())
                            val cName = kenalPastiKarakteristik(cUuid)
                            val prop = huraikanSifat(c.properties)
                            "   └ C: $cName [$cUuid] ($prop)"
                        }
                        "◆ Servis: $sName ($sUuid)\n$chars"
                    }
                    mainHandler.post {
                        onServicesDiscovered?.invoke(list, peratusBateriTerkini)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sambung(device: BluetoothDevice) {
        tutup()
        onStatusChange?.invoke("Menghantar jabat tangan sambungan...", false)
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun putuskan() {
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun tutup() {
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            // Elak crash
        }
        bluetoothGatt = null
        isConnected = false
    }

    private fun formatUuidRingkas(uuidStr: String): String {
        return if (uuidStr.length >= 8 && uuidStr.startsWith("0000") && uuidStr.contains("-0000-1000-8000-00805F9B34FB", ignoreCase = true)) {
            "0x" + uuidStr.substring(4, 8).uppercase()
        } else {
            uuidStr.take(8).uppercase()
        }
    }

    private fun kenalPastiServis(shortUuid: String): String {
        return when (shortUuid.uppercase()) {
            "0X180F" -> "Battery Service"
            "0X180A" -> "Device Information"
            "0X1800" -> "Generic Access"
            "0X1801" -> "Generic Attribute"
            "0X180D" -> "Heart Rate"
            "0X181A" -> "Environmental Sensing"
            "0XFEAA" -> "Google Eddystone"
            "0XFE2C" -> "Google Fast Pair"
            else -> "Vendor Custom"
        }
    }

    private fun kenalPastiKarakteristik(shortUuid: String): String {
        return when (shortUuid.uppercase()) {
            "0X2A19" -> "Battery Level (%)"
            "0X2A00" -> "Device Name"
            "0X2A29" -> "Manufacturer Name"
            "0X2A24" -> "Model Number"
            "0X2A26" -> "Firmware Version"
            "0X2A28" -> "Software Version"
            else -> "Data Channel"
        }
    }

    private fun huraikanSifat(prop: Int): String {
        val sifat = mutableListOf<String>()
        if (prop and BluetoothGattCharacteristic.PROPERTY_READ != 0) sifat.add("Read")
        if (prop and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) sifat.add("Write")
        if (prop and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) sifat.add("WriteNoResp")
        if (prop and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) sifat.add("Notify")
        if (prop and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) sifat.add("Indicate")
        return if (sifat.isEmpty()) "Standard" else sifat.joinToString(",")
    }
}
