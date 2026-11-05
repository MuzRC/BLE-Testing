package com.muz.bletesting.decoder

import com.muz.bletesting.model.BeaconInfo
import com.muz.bletesting.utils.BleParserUtils
import java.nio.ByteBuffer
import java.util.UUID

object BeaconDecoder {

    /**
     * Memeriksa dan mengekstrak data Apple iBeacon
     * Syarat: Manufacturer ID = 0x004C (Apple), Header = 0x02 0x15
     */
    fun kesanIBeacon(manufacturerId: Int, payload: ByteArray): BeaconInfo.IBeacon? {
        if (manufacturerId != 0x004C || payload.size < 23) {
            return null
        }

        // Sub-type 0x02 (iBeacon) dan Length 0x15 (21 bait baki)
        if (payload[0] != 0x02.toByte() || payload[1] != 0x15.toByte()) {
            return null
        }

        return try {
            // Bait 2 hingga 17 = 16-bait Proximity UUID
            val bb = ByteBuffer.wrap(payload, 2, 16)
            val mostSig = bb.long
            val leastSig = bb.long
            val uuid = UUID(mostSig, leastSig).toString().uppercase()

            // Bait 18 - 19 = Major ID (16-bit)
            val major = ((payload[18].toInt() and 0xFF) shl 8) or (payload[19].toInt() and 0xFF)

            // Bait 20 - 21 = Minor ID (16-bit)
            val minor = ((payload[20].toInt() and 0xFF) shl 8) or (payload[21].toInt() and 0xFF)

            // Bait 22 = Measured Power / TX Power pada jarak 1 meter
            val txPower = payload[22].toInt()

            BeaconInfo.IBeacon(uuid, major, minor, txPower)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Memeriksa dan mengekstrak data Google Eddystone
     * Syarat: Service UUID mengandungi 0xFEAA
     */
    fun kesanEddystone(serviceUuid: String?, serviceData: ByteArray): BeaconInfo.Eddystone? {
        if (serviceUuid == null || !serviceUuid.contains("FEAA", ignoreCase = true) || serviceData.isEmpty()) {
            return null
        }

        val frameTypeByte = serviceData[0].toInt() and 0xFF

        return when (frameTypeByte) {
            // Eddystone-UID (Pengecam unik mesin)
            0x00 -> {
                if (serviceData.size < 18) return null
                val txPower = serviceData[1].toInt()
                val namespace = BleParserUtils.byteArrayToHex(serviceData.copyOfRange(2, 12))
                val instance = BleParserUtils.byteArrayToHex(serviceData.copyOfRange(12, 18))
                BeaconInfo.Eddystone(
                    frameType = "UID",
                    namespace = namespace,
                    instance = instance,
                    txPower = txPower
                )
            }
            // Eddystone-URL (Pautan laman web terus dari udara)
            0x10 -> {
                if (serviceData.size < 3) return null
                val txPower = serviceData[1].toInt()
                val url = huraikanEddystoneUrl(serviceData.copyOfRange(2, serviceData.size))
                BeaconInfo.Eddystone(
                    frameType = "URL",
                    url = url,
                    txPower = txPower
                )
            }
            // Eddystone-TLM (Telemetri: bateri / suhu)
            0x20 -> {
                BeaconInfo.Eddystone(frameType = "TLM (Telemetri)")
            }
            else -> null
        }
    }

    private fun huraikanEddystoneUrl(urlBytes: ByteArray): String {
        if (urlBytes.isEmpty()) return ""

        val prefix = when (urlBytes[0].toInt() and 0xFF) {
            0x00 -> "http://www."
            0x01 -> "https://www."
            0x02 -> "http://"
            0x03 -> "https://"
            else -> ""
        }

        val expansionMap = mapOf(
            0x00 to ".com/", 0x01 to ".org/", 0x02 to ".edu/", 0x03 to ".net/",
            0x04 to ".info/", 0x05 to ".biz/", 0x06 to ".gov/", 0x07 to ".com",
            0x08 to ".org", 0x09 to ".edu", 0x0A to ".net", 0x0B to ".info",
            0x0C to ".biz", 0x0D to ".gov"
        )

        val sb = StringBuilder(prefix)
        for (i in 1 until urlBytes.size) {
            val b = urlBytes[i].toInt() and 0xFF
            val exp = expansionMap[b]
            if (exp != null) {
                sb.append(exp)
            } else if (b in 32..126) {
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }
}
