package com.muz.bletesting.utils

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

object HexDiffHelper {

    data class DiffResult(
        val visualSpannable: SpannableStringBuilder,
        val totalBaitAsal: Int,
        val totalBaitBaru: Int,
        val baitBertukar: Int,
        val baitSama: Int
    )

    /**
     * Membandingkan dua string Hex bait demi bait.
     * Hijau (#81C784) = Bait sama / statik
     * Merah (#FF5252) = Bait berubah (cth: rolling code / kaunter telemetri)
     * Biru (#64B5F6)  = Bait tambahan
     */
    fun bandingkan(hexAsalRaw: String, hexBaruRaw: String): DiffResult {
        val asal = bersihkanHex(hexAsalRaw)
        val baru = bersihkanHex(hexBaruRaw)

        val bytesAsal = asal.chunked(2)
        val bytesBaru = baru.chunked(2)

        val ssb = SpannableStringBuilder()
        var bertukar = 0
        var sama = 0

        val maxLen = maxOf(bytesAsal.size, bytesBaru.size)

        for (i in 0 until maxLen) {
            val bAsal = bytesAsal.getOrNull(i)
            val bBaru = bytesBaru.getOrNull(i)

            val startPos = ssb.length

            when {
                bAsal != null && bBaru != null -> {
                    if (bAsal.equals(bBaru, ignoreCase = true)) {
                        ssb.append("$bBaru ")
                        ssb.setSpan(
                            ForegroundColorSpan(Color.parseColor("#81C784")),
                            startPos,
                            startPos + 2,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        sama++
                    } else {
                        // Bait berubah: Paparkan nilai baharu dengan warna merah pekat dan tebal
                        ssb.append("$bBaru ")
                        ssb.setSpan(
                            ForegroundColorSpan(Color.parseColor("#FF5252")),
                            startPos,
                            startPos + 2,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        ssb.setSpan(
                            StyleSpan(Typeface.BOLD),
                            startPos,
                            startPos + 2,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        bertukar++
                    }
                }
                bAsal == null && bBaru != null -> {
                    // Bait lebih/tambahan pada paket baru
                    ssb.append("$bBaru ")
                    ssb.setSpan(
                        ForegroundColorSpan(Color.parseColor("#64B5F6")),
                        startPos,
                        startPos + 2,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    bertukar++
                }
                bAsal != null && bBaru == null -> {
                    // Bait berkurang (hilang dalam paket baru)
                    ssb.append("-- ")
                    ssb.setSpan(
                        ForegroundColorSpan(Color.parseColor("#9E9E9E")),
                        startPos,
                        startPos + 2,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    bertukar++
                }
            }
        }

        return DiffResult(
            visualSpannable = ssb,
            totalBaitAsal = bytesAsal.size,
            totalBaitBaru = bytesBaru.size,
            baitBertukar = bertukar,
            baitSama = sama
        )
    }

    private fun bersihkanHex(hex: String): String {
        return hex.replace(" ", "").replace("\n", "").trim()
    }
}
