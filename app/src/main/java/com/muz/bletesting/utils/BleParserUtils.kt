package com.muz.bletesting.utils

import android.bluetooth.le.ScanRecord

object BleParserUtils {

    fun hexToByteArray(hex: String): ByteArray {
        return try {
            val cleanHex = hex.replace(" ", "").trim()
            val formatted = if (cleanHex.length % 2 != 0) "0$cleanHex" else cleanHex
            ByteArray(formatted.length / 2) { i ->
                formatted.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    fun kenalpastiSyarikat(id: Int): String {
        return when (id) {
            0x0006 -> "Microsoft"
            0x004C -> "Apple"
            0x0075 -> "Samsung"
            0x000F -> "Broadcom"
            0x0059 -> "Nordic Semiconductor"
            0x00E0 -> "Google"
            0x0157 -> "Anker"
            0x038F -> "Xiaomi"
            0x07D0 -> "Tuya Smart"
            -1 -> "Generic BLE"
            else -> "Vendor #$id"
        }
    }

    fun ekstrakTeksAscii(bytes: ByteArray): String? {
        val currentChunk = StringBuilder()
        for (b in bytes) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z' || c == '-' || c == '_') {
                currentChunk.append(c)
            } else {
                if (currentChunk.length >= 4) {
                    return currentChunk.toString()
                }
                currentChunk.clear()
            }
        }
        return if (currentChunk.length >= 4) currentChunk.toString() else null
    }

    fun cariNamaDalamRawBytes(bytes: ByteArray?): String? {
        if (bytes == null) return null
        return try {
            var index = 0
            while (index < bytes.size) {
                val length = bytes[index].toInt() and 0xFF
                if (length == 0 || index + 1 + length > bytes.size) break
                val type = bytes[index + 1].toInt() and 0xFF
                // 0x08 = Shortened Local Name, 0x09 = Complete Local Name
                if (type == 0x08 || type == 0x09) {
                    val nameBytes = bytes.copyOfRange(index + 2, index + 1 + length)
                    return String(nameBytes, Charsets.UTF_8).trim()
                }
                index += length + 1
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun tentukanNamaPeranti(record: ScanRecord, rawDeviceName: String?, manId: Int, payload: ByteArray): String {
        val officialName = record.deviceName ?: rawDeviceName
        if (!officialName.isNullOrBlank() && !officialName.contains("Tanpa Nama", true)) {
            return officialName
        }

        val rawName = cariNamaDalamRawBytes(record.bytes)
        if (!rawName.isNullOrBlank()) {
            return rawName
        }

        val ascii = ekstrakTeksAscii(payload)
        val namaSyarikat = kenalpastiSyarikat(manId)

        if (!ascii.isNullOrBlank()) {
            return "$namaSyarikat ($ascii)"
        }

        return "Peranti $namaSyarikat"
    }
}
