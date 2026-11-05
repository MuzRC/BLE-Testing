package com.muz.bletesting.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.muz.bletesting.R
import com.muz.bletesting.utils.HexDiffHelper

object PacketDiffDialog {

    fun tunjuk(
        context: Context,
        defaultHexAsal: String = "",
        defaultHexBaru: String = ""
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_packet_diff, null)

        val etHexAsal = view.findViewById<EditText>(R.id.etDiffHexAsal)
        val etHexBaru = view.findViewById<EditText>(R.id.etDiffHexBaru)
        val btnJalankan = view.findViewById<Button>(R.id.btnJalankanDiff)
        val tvOutput = view.findViewById<TextView>(R.id.tvDiffOutput)
        val tvStats = view.findViewById<TextView>(R.id.tvDiffStats)

        etHexAsal.setText(defaultHexAsal)
        etHexBaru.setText(defaultHexBaru)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton("Tutup", null)
            .create()

        val laksanaDiff = {
            val asal = etHexAsal.text.toString()
            val baru = etHexBaru.text.toString()

            if (asal.isBlank() && baru.isBlank()) {
                tvOutput.text = "Sila masukkan sekurang-kurangnya satu payload Hex."
            } else {
                val hasil = HexDiffHelper.bandingkan(asal, baru)
                tvOutput.text = hasil.visualSpannable
                tvStats.text = """
                    Bait Rujukan: ${hasil.totalBaitAsal} B | Bait Terkini: ${hasil.totalBaitBaru} B
                    Perubahan Dikesan: ${hasil.baitBertukar} bait | Kekal Sama: ${hasil.baitSama} bait
                """.trimIndent()
            }
        }

        btnJalankan.setOnClickListener {
            laksanaDiff()
        }

        // Jalankan perbandingan automatik jika data awal sudah ada
        if (defaultHexAsal.isNotBlank() && defaultHexBaru.isNotBlank()) {
            laksanaDiff()
        }

        dialog.show()
    }
}
