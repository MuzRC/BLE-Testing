package com.muz.bletesting.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import com.muz.bletesting.R
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.utils.BleParserUtils

object ManualInjectDialog {

    fun tunjuk(context: Context, onInject: (BleTarget) -> Unit) {
        val layout = LayoutInflater.from(context).inflate(R.layout.dialog_manual_inject, null)
        val etName = layout.findViewById<EditText>(R.id.etInjectName)
        val etVendorId = layout.findViewById<EditText>(R.id.etInjectVendorId)
        val etHex = layout.findViewById<EditText>(R.id.etInjectHex)

        AlertDialog.Builder(context)
            .setTitle("Suntik Payload Manual")
            .setView(layout)
            .setPositiveButton("Tetapkan Target") { _, _ ->
                val name = etName.text.toString().trim().ifBlank { "Custom Injected BLE" }
                val vIdStr = etVendorId.text.toString().trim()
                val hexStr = etHex.text.toString().trim().replace(" ", "")

                val vId = vIdStr.toIntOrNull() ?: -1
                val payload = BleParserUtils.hexToByteArray(hexStr)

                if (payload.isEmpty()) {
                    Toast.makeText(context, "Hex payload tidak sah atau kosong!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val target = BleTarget(
                    name = name,
                    address = "MANUAL:INJECT:00",
                    manufacturerId = vId,
                    manufacturerData = payload,
                    serviceUuid = null,
                    serviceData = ByteArray(0),
                    rssi = -50
                )
                onInject(target)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
