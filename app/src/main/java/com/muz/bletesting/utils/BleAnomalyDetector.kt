package com.muz.bletesting.utils

import com.muz.bletesting.model.BleTarget
import java.util.concurrent.ConcurrentLinkedQueue

object BleAnomalyDetector {

    data class AnomalyReport(
        val isUnderAttack: Boolean,
        val packetsPerSecond: Int,
        val suspiciousReason: String?,
        val attackType: String?
    )

    // Simpan rekod cap masa paket dalam tempoh 1 saat
    private val timestamps = ConcurrentLinkedQueue<Long>()
    private const val FLOOD_THRESHOLD_PPS = 30 // Lebih 30 paket/saat dianggap banjir tidak normal

    fun prosesPaket(target: BleTarget): AnomalyReport {
        val now = System.currentTimeMillis()
        timestamps.add(now)

        // Buang rekod yang lebih tua daripada 1 saat (1000ms)
        while (timestamps.isNotEmpty() && (now - timestamps.peek()!! > 1000)) {
            timestamps.poll()
        }

        val pps = timestamps.size

        // 1. Semak tanda tangan spam terkenal
        val signatureAttack = kesanTandaTanganSpam(target)

        // 2. Tentukan status ancaman
        val isFlooding = pps >= FLOOD_THRESHOLD_PPS

        val isUnderAttack = isFlooding || (signatureAttack != null)
        val reason = when {
            isFlooding && signatureAttack != null -> "Banjir trafik melampau bersama tandatangan serangan dikenal pasti!"
            isFlooding -> "Kadar pancaran terlalu laju ($pps paket/saat)! Kemungkinan serangan DoS radio."
            signatureAttack != null -> "Tandatangan peranti palsu / spam dikesan di udara."
            else -> null
        }

        return AnomalyReport(
            isUnderAttack = isUnderAttack,
            packetsPerSecond = pps,
            suspiciousReason = reason,
            attackType = signatureAttack
        )
    }

    fun reset() {
        timestamps.clear()
    }

    private fun kesanTandaTanganSpam(target: BleTarget): String? {
        val hex = if (target.isServiceData) {
            BleParserUtils.byteArrayToHex(target.serviceData)
        } else {
            BleParserUtils.byteArrayToHex(target.manufacturerData)
        }

        // Corak Apple Action Modal / Device Setup Spam (Vendor 0x004C)
        if (target.manufacturerId == 0x004C && hex.startsWith("0F05C0", ignoreCase = true)) {
            return "Apple Pop-up Flood (Flipper/ESP32)"
        }

        // Corak Microsoft Swift Pair Spam (Vendor 0x0006)
        if (target.manufacturerId == 0x0006 && hex.startsWith("030080", ignoreCase = true)) {
            return "Windows Swift Pair Spam"
        }

        // Corak Samsung Easy Setup Flood (Vendor 0x0075)
        if (target.manufacturerId == 0x0075 && hex.length >= 12 && hex.startsWith("010002", ignoreCase = true)) {
            return "Samsung Multi-Device Spam"
        }

        // Corak Google Fast Pair Spam (UUID 0xFE2C berulang dengan MAC rawak)
        if (target.serviceUuid?.contains("FE2C", ignoreCase = true) == true && hex.length == 6) {
            return "Google Fast Pair Flood"
        }

        return null
    }
}
