package com.muz.bletesting.storage

import android.content.Context
import android.content.SharedPreferences
import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.utils.BleParserUtils
import org.json.JSONArray
import org.json.JSONObject

class BleProfileStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("BLE_MULTI_PROFILES", Context.MODE_PRIVATE)

    fun simpanKeBookmark(target: BleTarget) {
        val senarai = dapatkanSemuaBookmark().toMutableList()
        val indexSediaAda = senarai.indexOfFirst { it.address == target.address }

        if (indexSediaAda != -1) {
            senarai[indexSediaAda] = target
        } else {
            senarai.add(0, target)
        }

        simpanSemua(senarai)
    }

    fun padamBookmark(address: String) {
        val senarai = dapatkanSemuaBookmark().toMutableList()
        senarai.removeAll { it.address == address }
        simpanSemua(senarai)
    }

    fun dapatkanSemuaBookmark(): List<BleTarget> {
        val jsonStr = prefs.getString("PROFILES_JSON", null) ?: return emptyList()
        val hasil = mutableListOf<BleTarget>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val mPayload = BleParserUtils.hexToByteArray(obj.optString("manHex", ""))
                val sPayload = BleParserUtils.hexToByteArray(obj.optString("sHex", ""))
                hasil.add(
                    BleTarget(
                        name = obj.getString("name"),
                        address = obj.getString("address"),
                        manufacturerId = obj.getInt("manId"),
                        manufacturerData = mPayload,
                        serviceUuid = obj.optString("sUuid").ifBlank { null },
                        serviceData = sPayload,
                        rssi = obj.optInt("rssi", 0),
                        lastSeen = obj.optLong("lastSeen", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return hasil
    }

    private fun simpanSemua(senarai: List<BleTarget>) {
        val arr = JSONArray()
        for (item in senarai) {
            val obj = JSONObject().apply {
                put("name", item.name)
                put("address", item.address)
                put("manId", item.manufacturerId)
                put("manHex", BleParserUtils.byteArrayToHex(item.manufacturerData))
                put("sUuid", item.serviceUuid ?: "")
                put("sHex", BleParserUtils.byteArrayToHex(item.serviceData))
                put("rssi", item.rssi)
                put("lastSeen", item.lastSeen)
            }
            arr.put(obj)
        }
        prefs.edit().putString("PROFILES_JSON", arr.toString()).apply()
    }
}
