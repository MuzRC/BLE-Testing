package com.muz.bletesting.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
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
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    var onStatusChange: ((statusText: String, isConnected: Boolean) -> Unit)? = null
    var onServicesDiscovered: ((senaraiServis: List<String>, peratusBateri: Int?) -> Unit)? = null
    var onWriteResult: ((berjaya: Boolean, msg: String) -> Unit)? = null
    var onNotifyDataReceived: ((charUuidShort: String, hex: String, textAscii: String) -> Unit)? = null

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
                    sb.append("◆ Servis: $sName\n   UUID Penuh: ${service.uuid}\n")

                    for (char in service.characteristics) {
                        val cUuidShort = formatUuidRingkas(char.uuid.toString())
                        val cName = kenalPastiKarakteristik(cUuidShort)
                        val prop = huraikanSifat(char.properties)
                        sb.append("   └ C: $cName [$cUuidShort] ($prop)\n      UUID: ${char.uuid}\n")
                    }
                    hasilServis.add(sb.toString().trimEnd())
                }

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

                if (gatt != null) {
                    val list = gatt.services.map { s ->
                        val sUuid = formatUuidRingkas(s.uuid.toString())
                        val sName = kenalPastiServis(sUuid)
                        val chars = s.characteristics.joinToString("\n") { c ->
                            val cUuid = formatUuidRingkas(c.uuid.toString())
                            val cName = kenalPastiKarakteristik(cUuid)
                            val prop = huraikanSifat(c.properties)
                            "   └ C: $cName [$cUuid] ($prop)\n      UUID: ${c.uuid}"
                        }
                        "◆ Servis: $sName\n   UUID Penuh: ${s.uuid}\n$chars"
                    }
                    mainHandler.post {
                        onServicesDiscovered?.invoke(list, peratusBateriTerkini)
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            mainHandler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    onWriteResult?.invoke(true, "Bait berjaya ditulis ke perkakasan!")
                } else {
                    onWriteResult?.invoke(false, "Gagal menulis bait. Ralat kod: $status")
                }
            }
        }

        // Menerima data semburan berterusan (Notify / Indicate)
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            val data = characteristic?.value ?: return
            prosesDataMasuk(characteristic.uuid.toString(), data)
        }
    }

    private fun prosesDataMasuk(uuidStr: String, data: ByteArray) {
        val shortUuid = formatUuidRingkas(uuidStr)
        val hex = BleParserUtils.byteArrayToHex(data)
        val ascii = data.map { b -> if (b in 32..126) b.toInt().toChar() else '.' }.joinToString("")

        mainHandler.post {
            onNotifyDataReceived?.invoke(shortUuid, hex, ascii)
        }
    }

    /**
     * Mengaktifkan atau mematikan siaran Notify/Indicate pada perkakasan
     */
    @SuppressLint("MissingPermission")
    fun tukarLanggananNotify(charUuidStr: String, aktifkan: Boolean): Boolean {
        val gatt = bluetoothGatt ?: run {
            onWriteResult?.invoke(false, "Tiada sambungan aktif!")
            return false
        }

        val targetChar = cariKarakteristik(charUuidStr) ?: run {
            onWriteResult?.invoke(false, "Karakteristik tidak dijumpai!")
            return false
        }

        val prop = targetChar.properties
        val isNotify = (prop and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
        val isIndicate = (prop and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)

        if (!isNotify && !isIndicate) {
            onWriteResult?.invoke(false, "Karakteristik ini tidak menyokong Notify atau Indicate!")
            return false
        }

        // 1. Beritahu lapisan Bluetooth telefon untuk dengar
        gatt.setCharacteristicNotification(targetChar, aktifkan)

        // 2. Beritahu cip mikropengawal luaran melalui descriptor CCCD (0x2902)
        val descriptor = targetChar.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            onWriteResult?.invoke(false, "Descriptor CCCD (0x2902) tiada pada karakteristik ini!")
            return false
        }

        val nilaiDescriptor = when {
            !aktifkan -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            isNotify -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeDescriptor(descriptor, nilaiDescriptor)
            status == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = nilaiDescriptor
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    fun tulisHex(charUuidStr: String, hexPayload: String): Boolean {
        val gatt = bluetoothGatt ?: run {
            onWriteResult?.invoke(false, "Tiada sambungan aktif!")
            return false
        }

        val cleanHex = hexPayload.replace(" ", "").trim()
        val dataBytes = BleParserUtils.hexToByteArray(cleanHex)
        if (dataBytes.isEmpty()) {
            onWriteResult?.invoke(false, "Format Hex tidak sah atau kosong!")
            return false
        }

        val targetChar = cariKarakteristik(charUuidStr) ?: run {
            onWriteResult?.invoke(false, "Karakteristik tidak ditemui!")
            return false
        }

        val prop = targetChar.properties
        val canWrite = (prop and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                (prop and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)

        if (!canWrite) {
            onWriteResult?.invoke(false, "Karakteristik ini bersifat Read-Only!")
            return false
        }

        val writeType = if (prop and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt.writeCharacteristic(targetChar, dataBytes, writeType)
            status == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            targetChar.value = dataBytes
            targetChar.writeType = writeType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(targetChar)
        }
    }

    private fun cariKarakteristik(charUuidStr: String): BluetoothGattCharacteristic? {
        val gatt = bluetoothGatt ?: return null
        val targetCharUuid = try {
            if (charUuidStr.length == 4 || charUuidStr.length == 6) {
                val hex4 = charUuidStr.removePrefix("0x").removePrefix("0X")
                UUID.fromString("0000$hex4-0000-1000-8000-00805F9B34FB")
            } else {
                UUID.fromString(charUuidStr)
            }
        } catch (e: Exception) {
            return null
        }

        for (service in gatt.services) {
            val c = service.getCharacteristic(targetCharUuid)
            if (c != null) return c
        }
        return null
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
            // Elak ralat penutupan mendadak
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
            "0X2A37" -> "Heart Rate Measurement"
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
