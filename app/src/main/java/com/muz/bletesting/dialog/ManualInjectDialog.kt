package com.muz.bletesting.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.muz.bletesting.R
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.storage.BlePresetLibrary
import com.muz.bletesting.utils.BleParserUtils

object ManualInjectDialog {

    fun tunjuk(context: Context, onInject: (BleTarget) -> Unit) {
        val layout = LayoutInflater.from(context).inflate(R.layout.dialog_manual_inject, null)
        val btnPilihPreset = layout.findViewById<Button>(R.id.btnPilihPreset)
        val etName = layout.findViewById<EditText>(R.id.etInjectName)
        val etVendorId = layout.findViewById<EditText>(R.id.etInjectVendorId)
        val etHex = layout.findViewById<EditText>(R.id.etInjectHex)

        var selectedServiceUuid: String? = null

        // Butang untuk memilih daripada senarai pustaka templat siap
        btnPilihPreset.setOnClickListener {
            val senarai = BlePresetLibrary.senaraiPreset
            val tajukList = senarai.map { "[${it.category}]\n${it.title}\n${it.description}" }.toTypedArray()

            AlertDialog.Builder(context)
                .setTitle("Pilih Templat Signal BLE")
                .setItems(tajukList) { _, index ->
                    val preset = senarai[index]
                    etName.setText(preset.title)
                    etVendorId.setText(if (preset.manufacturerId != -1) preset.manufacturerId.toString() else "-1")
                    etHex.setText(preset.hexData)
                    selectedServiceUuid = preset.serviceUuid
                    Toast.makeText(context, "${preset.title} dimuatkan!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        AlertDialog.Builder(context)
            .setTitle("Suntik Payload Manual & Templat")
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
                    manufacturerData = if (selectedServiceUuid == null) payload else ByteArray(0),
                    serviceUuid = selectedServiceUuid,
                    serviceData = if (selectedServiceUuid != null) payload else ByteArray(0),
                    rssi = -50
                )
                onInject(target)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
