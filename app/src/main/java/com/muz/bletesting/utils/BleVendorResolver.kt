package com.muz.bletesting.utils

object BleVendorResolver {

    /**
     * Pengecam Bluetooth SIG Company ID (16-bit Vendor Identifier)
     */
    fun huraikanCompanyId(companyId: Int): String {
        return when (companyId) {
            0x004C -> "Apple, Inc."
            0x0006 -> "Microsoft Corp."
            0x0075 -> "Samsung Electronics"
            0x00E0 -> "Google LLC"
            0x0059 -> "Nordic Semiconductor"
            0x000D -> "Texas Instruments"
            0x02E5 -> "Espressif Systems (ESP32)"
            0x0157 -> "Anhui Huami (Xiaomi / Amazfit)"
            0x0171 -> "Amazon Web Services"
            0x0822 -> "Tuya Smart Inc."
            0x0087 -> "Garmin International"
            0x000A -> "Qualcomm Technologies"
            0x0001 -> "Nokia Mobile"
            0x0002 -> "Intel Corp."
            0x000F -> "Broadcom Corp."
            0x0030 -> "STMicroelectronics"
            0x001D -> "Qualcomm (CSR)"
            0x01D3 -> "Thermopro"
            0x02B2 -> "Telink Semiconductor"
            0x038F -> "Xiaomi Inc."
            0x0499 -> "Ruuvi Innovations"
            0xFFFF -> "Format Ujian / Tidak Didaftar"
            -1 -> "Tiada Vendor ID"
            else -> "Vendor SIG: 0x${Integer.toHexString(companyId).uppercase()}"
        }
    }

    /**
     * Pengecam IEEE OUI (3 Bait Pertama Alamat MAC) & Jenis Alamat BLE
     */
    fun huraikanMacVendor(macAddress: String): String {
        val cleanMac = macAddress.uppercase().trim()
        val parts = cleanMac.split(":")
        if (parts.size < 3) return "MAC Tidak Sah"

        // Semak jenis alamat MAC BLE mengikut spesifikasi teras Bluetooth
        // 2 bit paling atas pada bait pertama menentukan jenis alamat rawak:
        // 0b11xxxxxx (0xC0..0xFF) = Static Random Address
        // 0b01xxxxxx (0x40..0x7F) = Resolvable Private Address (RPA - Apple/Android privacy rotation)
        // 0b00xxxxxx (0x00..0x3F) = Non-Resolvable Private Address
        val firstByte = parts[0].toIntOrNull(16) ?: 0
        val isRandom = (firstByte and 0xC0) == 0x40 || (firstByte and 0xC0) == 0xC0

        val oui = "${parts[0]}:${parts[1]}:${parts[2]}"

        val knownOuiVendor = when (oui) {
            // Espressif (ESP32 / ESP8266)
            "24:0A:C4", "24:62:AB", "30:AE:A4", "3C:71:BF", "7C:DF:A1",
            "84:CC:A8", "A4:CF:12", "C4:4F:33", "D8:A0:1D", "EC:FA:BC" -> "Espressif (ESP32)"

            // Nordic Semiconductor
            "F4:CE:36", "C0:98:E5", "DC:A6:32", "E4:5F:01" -> "Nordic Semi SoC"

            // Raspberry Pi Trading Ltd
            "B8:27:EB", "DC:A6:32", "E4:5F:01", "28:CD:C1" -> "Raspberry Pi"

            // Apple Inc
            "00:03:93", "00:05:02", "00:0A:27", "00:0A:95", "00:10:FA",
            "F0:18:98", "F4:F1:5A", "F8:FF:C2", "FC:25:3F" -> "Apple Perkakasan"

            // Samsung Electronics
            "00:07:AB", "00:12:47", "00:15:99", "18:67:B0", "48:44:F7" -> "Samsung Perkakasan"

            // Texas Instruments
            "00:17:E9", "18:93:D7", "40:EC:99", "54:6C:0E" -> "Texas Instruments"

            // Xiaomi Communications
            "00:EC:0A", "04:CF:8C", "18:59:36", "34:80:B3", "78:02:F8" -> "Xiaomi HW"

            // Tuya Smart Inc
            "D8:1F:12", "10:5A:F7", "70:89:71", "A0:92:08" -> "Tuya Smart IoT"

            else -> null
        }

        return when {
            knownOuiVendor != null -> knownOuiVendor
            isRandom -> "Alamat Rawak / RPA (Anti-Tracking)"
            else -> "OUI Umum ($oui)"
        }
    }
}
