package com.muz.bletesting.dialog

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.muz.bletesting.R
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.model.TrackerInfo
import com.muz.bletesting.utils.BleTrackerDetector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TrackerFinderDialog(context: Context) {

    private val dialog: AlertDialog
    private val layoutStatus: LinearLayout
    private val tvBadge: TextView
    private val tvResults: TextView
    private val svResults: ScrollView

    private val detectedTrackers = ConcurrentHashMap<String, TrackerInfo>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val isShowing: Boolean
        get() = dialog.isShowing

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_tracker_finder, null)
        layoutStatus = view.findViewById(R.id.layoutTrackerStatus)
        tvBadge = view.findViewById(R.id.tvTrackerCountBadge)
        tvResults = view.findViewById(R.id.tvTrackerResults)
        svResults = view.findViewById(R.id.svTrackers)
        val btnClear = view.findViewById<Button>(R.id.btnClearTrackers)
        val btnClose = view.findViewById<Button>(R.id.btnCloseTrackerDialog)

        dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        btnClear.setOnClickListener {
            detectedTrackers.clear()
            tvBadge.text = "0 Tag Mencurigakan Dikesan"
            tvBadge.setTextColor(Color.parseColor("#00E676"))
            layoutStatus.setBackgroundColor(Color.parseColor("#1B2032"))
            tvResults.text = "[Senarai dibersihkan. Mengimbas semula...]"
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }

    fun semakPaket(target: BleTarget) {
        val info = BleTrackerDetector.kesanTag(target) ?: return

        detectedTrackers[info.identifier] = info
        kemasKiniPaparan()
    }

    private fun kemasKiniPaparan() {
        val total = detectedTrackers.size
        if (total > 0) {
            layoutStatus.setBackgroundColor(Color.parseColor("#3B141E"))
            tvBadge.text = "⚠️ $total TAG PENJEJAK DIKESAN BERHAMPIRAN!"
            tvBadge.setTextColor(Color.parseColor("#FF5252"))

            val sb = StringBuilder()
            val now = System.currentTimeMillis()

            detectedTrackers.values.sortedByDescending { it.rssi }.forEach { t ->
                val stamp = timeFormat.format(Date(t.lastSeen))
                val saatLepas = (now - t.lastSeen) / 1000
                sb.append("● ${t.brand} - ${t.model}\n")
                sb.append("   MAC: ${t.identifier} | Kuasa: ${t.rssi} dBm\n")
                sb.append("   Kategori Risiko: ${t.riskLevel}\n")
                sb.append("   Dikesan Terkini: $stamp ($saatLepas saat lalu)\n\n")
            }

            tvResults.text = sb.toString().trimEnd()
            svResults.post { svResults.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }
}
