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
import com.muz.bletesting.utils.BleAnomalyDetector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnomalyDetectorDialog(context: Context) {

    private val dialog: AlertDialog
    private val layoutThreat: LinearLayout
    private val tvBadge: TextView
    private val tvDetail: TextView
    private val tvPps: TextView
    private val tvLog: TextView
    private val svLog: ScrollView

    private val logBuilder = StringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val isShowing: Boolean
        get() = dialog.isShowing

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_anomaly_detector, null)
        layoutThreat = view.findViewById(R.id.layoutThreatStatus)
        tvBadge = view.findViewById(R.id.tvThreatBadge)
        tvDetail = view.findViewById(R.id.tvThreatDetail)
        tvPps = view.findViewById(R.id.tvPpsCounter)
        tvLog = view.findViewById(R.id.tvAnomalyLog)
        svLog = view.findViewById(R.id.svAnomalyLog)
        val btnClose = view.findViewById<Button>(R.id.btnCloseAnomaly)

        dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            BleAnomalyDetector.reset()
        }
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }

    fun prosesPaketMasuk(target: BleTarget) {
        val report = BleAnomalyDetector.prosesPaket(target)

        tvPps.text = "${report.packetsPerSecond} PPS"

        if (report.isUnderAttack) {
            layoutThreat.setBackgroundColor(Color.parseColor("#3B141E"))
            tvBadge.text = "⚠️ AMARAN: BANJIR SPAM DIKESAN!"
            tvBadge.setTextColor(Color.parseColor("#FF5252"))
            tvDetail.text = report.suspiciousReason ?: "Aktiviti anomali tinggi di udara."

            val stamp = timeFormat.format(Date())
            val jenis = report.attackType ?: "Trafik Padat"
            logBuilder.append("[$stamp] $jenis | Sumber: ${target.address} (${target.rssi} dBm)\n")
            tvLog.text = logBuilder.toString()
            svLog.post { svLog.fullScroll(ScrollView.FOCUS_DOWN) }
        } else {
            layoutThreat.setBackgroundColor(Color.parseColor("#1B2032"))
            tvBadge.text = "STATUS: UDARA BERSIH"
            tvBadge.setTextColor(Color.parseColor("#00E676"))
            tvDetail.text = "Kadar pancaran normal ($report.packetsPerSecond paket/saat)."
        }
    }
}
