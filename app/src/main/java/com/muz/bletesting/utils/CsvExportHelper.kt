package com.muz.bletesting.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.muz.bletesting.model.BleTarget
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExportHelper {

    fun eksportDanKongsi(context: Context, senarai: List<BleTarget>): Boolean {
        if (senarai.isEmpty()) return false

        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "BLE_Log_$timeStamp.csv"
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, fileName)

            FileWriter(file).use { writer ->
                writer.append("Nama Peranti,Alamat MAC,RSSI (dBm),ID Vendor,Service UUID,Payload Hex,Masa Dikesan\n")
                val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                for (target in senarai) {
                    val payload = if (target.isServiceData) {
                        BleParserUtils.byteArrayToHex(target.serviceData)
                    } else {
                        BleParserUtils.byteArrayToHex(target.manufacturerData)
                    }
                    val formattedDate = dateFmt.format(Date(target.lastSeen))
                    val sUuid = target.serviceUuid ?: "N/A"
                    val vId = if (target.manufacturerId != -1) target.manufacturerId.toString() else "N/A"

                    writer.append("\"${target.name}\",")
                    writer.append("\"${target.address}\",")
                    writer.append("${target.rssi},")
                    writer.append("$vId,")
                    writer.append("\"$sUuid\",")
                    writer.append("\"$payload\",")
                    writer.append("\"$formattedDate\"\n")
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Log Imbasan BLE - $timeStamp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Hantar fail log CSV melalui:"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
