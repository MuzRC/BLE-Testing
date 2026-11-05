package com.muz.bletesting.dialog

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.muz.bletesting.R
import com.muz.bletesting.ble.BleGattManager
import com.muz.bletesting.model.BleTarget

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
        
        // Komponen baharu untuk Write Characteristic
        val etCharUuid = view.findViewById<EditText>(R.id.etWriteCharUuid)
        val etHexValue = view.findViewById<EditText>(R.id.etWriteHexValue)
        val btnSendHex = view.findViewById<Button>(R.id.btnSendHex)

        val btnDisconnect = view.findViewById<Button>(R.id.btnGattDisconnect)
        val btnClose = view.findViewById<Button>(R.id.btnGattClose)

        tvTargetInfo.text = "Peranti: ${target.name} [${target.address}]"

        val gattManager = BleGattManager(context)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        gattManager.onStatusChange = { msg, isConnected ->
            tvStatus.text = msg
            btnDisconnect.isEnabled = isConnected
            btnSendHex.isEnabled = isConnected
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

        // Paparkan maklum balas bila cip selesai proses arahan Write
        gattManager.onWriteResult = { berjaya, msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            if (berjaya) {
                etHexValue.setText("")
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

        // Sambung terus menggunakan perkakasan radio Bluetooth fizikal
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
