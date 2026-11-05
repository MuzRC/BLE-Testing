package com.muz.bletesting.utils

import com.muz.bletesting.model.BleTarget
import com.muz.bletesting.model.TrackerInfo

object BleTrackerDetector {

    /**
     * Memeriksa sama ada paket BLE yang diterima adalah daripada tag penjejak komersial
     */
    fun kesanTag(target: BleTarget): TrackerInfo? {
        val hex = if (target.isServiceData) {
            BleParserUtils.byteArrayToHex(target.serviceData)
        } else {
            BleParserUtils.byteArrayToHex(target.manufacturerData)
        }

        // 1. Pengesanan Apple AirTag / Find My Network
        // Apple Vendor ID = 0x004C. Sub-jenis Offline Finding bermula dengan bait 0x12 dan panjang 29 bait (58 aksara hex)
        if (target.manufacturerId == 0x004C && target.manufacturerData.size >= 27) {
            val bytes = target.manufacturerData
            if (bytes[0] == 0x12.toByte() && bytes[1] == 0x19.toByte()) {
                return TrackerInfo(
                    brand = "Apple",
                    model = "AirTag / Find My Network Tag",
                    identifier = target.address,
                    rssi = target.rssi,
                    riskLevel = "TINGGI",
                    description = "Tag penjejak Apple dikesan memancar suar lokasi berhampiran."
                )
            }
        }

        // 2. Pengesanan Samsung Galaxy SmartTag / SmartThings Find
        // Samsung Vendor ID = 0x0075. SmartTag memancar suar privasi khusus
        if (target.manufacturerId == 0x0075 && target.manufacturerData.size in 12..24) {
            if (hex.startsWith("010002", ignoreCase = true) || hex.startsWith("4209", ignoreCase = true)) {
                return TrackerInfo(
                    brand = "Samsung",
                    model = "Galaxy SmartTag / SmartThings",
                    identifier = target.address,
                    rssi = target.rssi,
                    riskLevel = "TINGGI",
                    description = "Penjejak Samsung SmartTag dikesan berhampiran lokasi anda."
                )
            }
        }

        // 3. Pengesanan Tile Tracker
        // Tile menggunakan Service UUID khusus 0xFEED
        if (target.serviceUuid?.contains("FEED", ignoreCase = true) == true) {
            return TrackerInfo(
                brand = "Tile",
                model = "Tile Mate / Pro Tracker",
                identifier = target.address,
                rssi = target.rssi,
                riskLevel = "SEDERHANA",
                description = "Tag penjejak Tile Network dikesan di udara."
            )
        }

        return null
    }
}
