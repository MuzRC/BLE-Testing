package com.muz.bletesting.storage

import android.content.Context
import android.content.SharedPreferences
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.utils.BleParserUtils

class BleStorageManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("BLE_STORAGE", Context.MODE_PRIVATE)

    fun simpanTarget(target: BleTarget) {
        val manHex = BleParserUtils.byteArrayToHex(target.manufacturerData)
        val sHex = BleParserUtils.byteArrayToHex(target.serviceData)

        prefs.edit()
            .putString("SAVED_NAME", target.name)
            .putString("SAVED_MAC", target.address)
            .putInt("SAVED_MAN_ID", target.manufacturerId)
            .putString("SAVED_HEX", manHex)
            .putString("SAVED_SERVICE_UUID", target.serviceUuid)
            .putString("SAVED_SERVICE_HEX", sHex)
            .apply()
    }

    fun muatTarget(): BleTarget? {
        val name = prefs.getString("SAVED_NAME", null) ?: return null
        val mac = prefs.getString("SAVED_MAC", "Alamat Disimpan") ?: "Alamat Disimpan"
        val manId = prefs.getInt("SAVED_MAN_ID", -1)
        val manHex = prefs.getString("SAVED_HEX", "") ?: ""
        val sUuid = prefs.getString("SAVED_SERVICE_UUID", null)
        val sHex = prefs.getString("SAVED_SERVICE_HEX", "") ?: ""

        val manPayload = BleParserUtils.hexToByteArray(manHex)
        val sPayload = BleParserUtils.hexToByteArray(sHex)

        return BleTarget(
            name = name,
            address = mac,
            manufacturerId = manId,
            manufacturerData = manPayload,
            serviceUuid = sUuid,
            serviceData = sPayload,
            rssi = 0
        )
    }

    fun dapatkanDisplayData(target: BleTarget): String {
        return if (target.isServiceData) {
            BleParserUtils.byteArrayToHex(target.serviceData)
        } else {
            BleParserUtils.byteArrayToHex(target.manufacturerData)
        }
    }
}
