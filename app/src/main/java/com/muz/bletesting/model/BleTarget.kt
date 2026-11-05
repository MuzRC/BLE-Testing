package com.muz.bletesting.model

data class BleTarget(
    val name: String,
    val address: String,
    val manufacturerId: Int = -1,
    val manufacturerData: ByteArray = ByteArray(0),
    val serviceUuid: String? = null,
    val serviceData: ByteArray = ByteArray(0),
    val rssi: Int = 0,
    var lastSeen: Long = System.currentTimeMillis()
) {
    val isServiceData: Boolean
        get() = manufacturerId == -1 && serviceUuid != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BleTarget
        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}
