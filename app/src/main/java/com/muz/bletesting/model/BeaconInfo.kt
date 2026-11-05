package com.muz.bletesting.model

sealed class BeaconInfo {

    // Format Apple iBeacon rasmi
    data class IBeacon(
        val uuid: String,
        val major: Int,
        val minor: Int,
        val txPower: Int
    ) : BeaconInfo() {
        override fun toString(): String {
            return "iBeacon [Major: $major, Minor: $minor, TX: ${txPower}dBm]\nUUID: $uuid"
        }
    }

    // Format Google Eddystone (URL web atau Pengecam UID)
    data class Eddystone(
        val frameType: String, // UID, URL, atau TLM
        val url: String? = null,
        val namespace: String? = null,
        val instance: String? = null,
        val txPower: Int = 0
    ) : BeaconInfo() {
        override fun toString(): String {
            return if (!url.isNullOrBlank()) {
                "Eddystone-URL [$url] (TX: ${txPower}dBm)"
            } else {
                "Eddystone-UID [Namespace: $namespace, Instance: $instance]"
            }
        }
    }
}
