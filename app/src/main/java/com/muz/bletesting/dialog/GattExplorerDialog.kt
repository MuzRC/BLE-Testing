package com.muz.bletesting.dialog

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.muz.bletesting.R
import com.muz.bletesting.ble.BleGattManager
import com.muz.bletesting.model.BleTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GattExplorerDialog {

    fun tunjuk(
        context: Context,
        bluetoothAdapter: BluetoothAdapter?,
        target: BleTarget
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_gatt_explorer, null)
        val tvTargetInfo = view.findViewById<TextView>(R.id.tvGattTargetInfo)
        val tvStatus = view.findViewById<TextView>(R.id.tvGattStatus)
        val tvBattery = view.findViewById<TextView>(R.id.tvBatteryBadge)
        val tvTree = view.findViewById<TextView>(R.id.tvGattServicesTree)
        val tvLiveLog = view.findViewById<TextView>(R.id.tvLiveStreamLog)
        val svLive = view.findViewById<ScrollView>(R.id.svLiveStream)

        val etCharUuid = view.findViewById<EditText>(R.id.etWriteCharUuid)
        val etHexValue = view.findViewById<EditText>(R.id.etWriteHexValue)
        val btnSendHex = view.findViewById<Button>(R.id.btnSendHex)
        val btnToggleNotify = view.findViewById<Button>(R.id.btnToggleNotify)

        val btnDisconnect = view.findViewById<Button>(R.id.btnGattDisconnect)
        val btnClose = view.findViewById<Button>(R.id.btnGattClose)

        tvTargetInfo.text = "Peranti: ${target.name} [${target.address}]"

        val gattManager = BleGattManager(context)
        var isSubscribed = false
        val logBuilder = StringBuilder()
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        gattManager.onStatusChange = { msg, isConnected ->
            tvStatus.text = msg
            btnDisconnect.isEnabled = isConnected
            btnSendHex.isEnabled = isConnected
            btnToggleNotify.isEnabled = isConnected
        }

        gattManager.onServicesDiscovered = { list, batteryPercent ->
            if (batteryPercent != null) {
                tvBattery.text = "Bateri: $batteryPercent%"
            } else {
                tvBattery.text = "Bateri: N/A"
            }

            if (list.isEmpty()) {
                tvTree.text = "Tiada servis umum dikesan pada peranti ini."
            } else {
                tvTree.text = list.joinToString("\n\n")
            }
        }

        // Tangkap data masuk masa nyata dari perkakasan
        gattManager.onNotifyDataReceived = { charUuid, hex, ascii ->
            val timestamp = timeFormat.format(Date())
            logBuilder.append("[$timestamp] $charUuid -> $hex ($ascii)\n")
            tvLiveLog.text = logBuilder.toString()
            svLive.post { svLive.fullScroll(ScrollView.FOCUS_DOWN) }
        }

        gattManager.onWriteResult = { berjaya, msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            if (berjaya) {
                etHexValue.setText("")
            }
        }

        // Butang suis langganan siaran langsung
        btnToggleNotify.setOnClickListener {
            val charUuid = etCharUuid.text.toString().trim()
            if (charUuid.isBlank()) {
                Toast.makeText(context, "Sila masukkan UUID Karakteristik!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isSubscribed = !isSubscribed
            val berjaya = gattManager.tukarLanggananNotify(charUuid, isSubscribed)
            if (berjaya) {
                if (isSubscribed) {
                    btnToggleNotify.text = "✖ Henti"
                    btnToggleNotify.setBackgroundColor(android.graphics.Color.parseColor("#FF5252"))
                    Toast.makeText(context, "Mendengar siaran langsung...", Toast.LENGTH_SHORT).show()
                } else {
                    btnToggleNotify.text = "📡 Langgan"
                    btnToggleNotify.setBackgroundColor(android.graphics.Color.parseColor("#00E5FF"))
                    Toast.makeText(context, "Langganan dihentikan.", Toast.LENGTH_SHORT).show()
                }
            } else {
                isSubscribed = !isSubscribed
            }
        }

        btnSendHex.setOnClickListener {
            val charUuid = etCharUuid.text.toString().trim()
            val hex = etHexValue.text.toString().trim()

            if (charUuid.isBlank() || hex.isBlank()) {
                Toast.makeText(context, "Sila masukkan UUID dan Data Hex!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            gattManager.tulisHex(charUuid, hex)
        }

        btnDisconnect.setOnClickListener {
            gattManager.putuskan()
            Toast.makeText(context, "Memutuskan sambungan...", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener {
            gattManager.putuskan()
            gattManager.tutup()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            gattManager.putuskan()
            gattManager.tutup()
        }

        try {
            val device = bluetoothAdapter?.getRemoteDevice(target.address)
            if (device != null) {
                dialog.show()
                gattManager.sambung(device)
            } else {
                Toast.makeText(context, "Peranti tidak sah!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ralat sambung: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
