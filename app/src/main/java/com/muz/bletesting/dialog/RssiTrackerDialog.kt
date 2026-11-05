package com.muz.bletesting.dialog

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import com.muz.bletesting.R
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.view.RssiWaveformView

class RssiTrackerDialog(
    context: Context,
    val target: BleTarget
) {
    private val dialog: AlertDialog
    private val tvTargetInfo: TextView
    private val tvLiveRssi: TextView
    private val tvZone: TextView
    private val waveformView: RssiWaveformView

    val isShowing: Boolean
        get() = dialog.isShowing

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_rssi_tracker, null)

        tvTargetInfo = view.findViewById(R.id.tvTrackerTargetInfo)
        tvLiveRssi = view.findViewById(R.id.tvLiveRssiValue)
        tvZone = view.findViewById(R.id.tvProximityZone)
        waveformView = view.findViewById(R.id.rssiWaveformView)
        val btnTutup = view.findViewById<Button>(R.id.btnTutupTracker)

        tvTargetInfo.text = "Sasaran: ${target.name} [${target.address}]"
        kemasKiniRssi(target.rssi)

        dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        btnTutup.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            waveformView.reset()
        }
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }

    fun kemasKiniRssi(rssi: Int) {
        tvLiveRssi.text = "$rssi dBm"
        waveformView.addRssi(rssi)

        when {
            rssi >= -60 -> {
                tvZone.text = "ZON: RAPAT (< 1 meter)"
                tvZone.setTextColor(Color.parseColor("#FF5252")) // Merah tanda sangat dekat
            }
            rssi >= -75 -> {
                tvZone.text = "ZON: SEDERHANA (1 - 3 meter)"
                tvZone.setTextColor(Color.parseColor("#00E676")) // Hijau
            }
            else -> {
                tvZone.text = "ZON: JAUH (> 3 meter)"
                tvZone.setTextColor(Color.parseColor("#FFB74D")) // Jingga
            }
        }
    }
}
