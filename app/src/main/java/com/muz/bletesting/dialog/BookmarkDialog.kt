package com.muz.bletesting.dialog

import android.app.AlertDialog
import android.content.Context
import android.widget.ArrayAdapter
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.storage.BleProfileStorage
import com.muz.bletesting.utils.BleParserUtils

object BookmarkDialog {

    fun tunjuk(
        context: Context,
        profileStorage: BleProfileStorage,
        onPilih: (BleTarget) -> Unit
    ) {
        val senarai = profileStorage.dapatkanSemuaBookmark()
        if (senarai.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("Senarai Bookmark Kosong")
                .setMessage("Belum ada peranti yang disimpan ke bookmark. Ketik mana-mana peranti semasa imbasan untuk simpan.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val tajukList = senarai.map { item ->
            val payload = if (item.isServiceData) item.serviceData else item.manufacturerData
            val hex = BleParserUtils.byteArrayToHex(payload)
            "${item.name}\nMAC: ${item.address} | Data: ${hex.take(16)}..."
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, tajukList)

        AlertDialog.Builder(context)
            .setTitle("Pilih Profil Disimpan (${senarai.size})")
            .setAdapter(adapter) { _, which ->
                onPilih(senarai[which])
            }
            .setNeutralButton("Tutup", null)
            .show()
    }
}
