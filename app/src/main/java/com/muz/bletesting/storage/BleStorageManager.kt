package com.muz.bletesting.storage

import android.content.Context
import android.content.SharedPreferences
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.utils.BleParserUtils

class BleStorageManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("BLE_STORAGE", Context.MODE_PRIVATE)

    fun simpanTarget(target: BleTarget) {
        val hex = BleParserUtils.byteArrayToHex(target.manufacturerData)
        prefs.edit()
            .putString("SAVED_NAME", target.name)
            .putString("SAVED_MAC", target.address)
            .putInt("SAVED_MAN_ID", target.manufacturerId)
            .putString("SAVED_HEX", hex)
            .apply()
    }

    fun muatTarget(): BleTarget? {
        val name = prefs.getString("SAVED_NAME", null) ?: return null
        val mac = prefs.getString("SAVED_MAC", "Alamat Disimpan") ?: "Alamat Disimpan"
        val manId = prefs.getInt("SAVED_MAN_ID", -1)
        val hex = prefs.getString("SAVED_HEX", null) ?: return null

        val payload = BleParserUtils.hexToByteArray(hex)
        return BleTarget(name, mac, manId, payload, 0)
    }

    fun dapatkanSavedHex(): String? {
        return prefs.getString("SAVED_HEX", null)
    }
}
