package com.muz.bletesting.storage

import com.muz.bletesting.model.BlePreset

object BlePresetLibrary {

    val senaraiPreset = listOf(
        // 1. Kategori: Beacon Navigasi & Lokasi
        BlePreset(
            title = "Apple iBeacon (Standard Navigasi)",
            category = "Beacon Lokasi",
            description = "Standard rasmi Apple iBeacon (UUID Standard, Major: 1, Minor: 2, TX: -59dBm).",
            manufacturerId = 0x004C,
            hexData = "0215E2C56DB5DFFB48D2B060D0F5A71096E000010002C5"
        ),
        BlePreset(
            title = "Google Eddystone-URL (Pautan Web)",
            category = "Beacon Lokasi",
            description = "Memancarkan pautan web 'https://www.google.com' terus ke udara.",
            serviceUuid = "0000FEAA-0000-1000-8000-00805F9B34FB",
            hexData = "10BA01676F6F676C6500"
        ),
        BlePreset(
            title = "Google Eddystone-UID (Pengecam Mesin)",
            category = "Beacon Lokasi",
            description = "Memancarkan kod Namespace dan Instance ID untuk sistem tracking fasiliti.",
            serviceUuid = "0000FEAA-0000-1000-8000-00805F9B34FB",
            hexData = "00BA0102030405060708090A0B0C0D0E0F10"
        ),

        // 2. Kategori: Ekosistem Pintar & Aksesori
        BlePreset(
            title = "Google Fast Pair (Pop-up Fon Pintar)",
            category = "Aksesori Pintar",
            description = "Format protokol Google Fast Pair (Service UUID 0xFE2C) untuk aksesori audio.",
            serviceUuid = "0000FE2C-0000-1000-8000-00805F9B34FB",
            hexData = "F44621"
        ),
        BlePreset(
            title = "Tuya Smart Beacon (Suis / Sensor IoT)",
            category = "Aksesori Pintar",
            description = "Paket protokol peranti pintar Tuya IoT (Vendor ID 2000).",
            manufacturerId = 2000,
            hexData = "8903000000C003788936D74EFBE01B08EB090C37C3675"
        ),

        // 3. Kategori: Audit Keselamatan & Privasi
        BlePreset(
            title = "Exposure Notification Dummy",
            category = "Audit & Keselamatan",
            description = "Protokol pengesanan kontak rapat Bluetooth (UUID 0xFD6F) dengan RPI dummy 16-bait.",
            serviceUuid = "0000FD6F-0000-1000-8000-00805F9B34FB",
            hexData = "4A172355544950113243CB050EA2FB44"
        ),
        BlePreset(
            title = "Generic AltBeacon (Format Terbuka)",
            category = "Beacon Lokasi",
            description = "Format suar terbuka standard industri (Radius Networks / Code 0xBEAC).",
            manufacturerId = 0x0118,
            hexData = "BEAC11223344556677889900AABBCCDDEEFF00010002C500"
        )
    )
}
