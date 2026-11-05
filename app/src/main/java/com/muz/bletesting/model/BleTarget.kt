package com.muz.bletesting.model

data class BleTarget(
    val name: String,
    val address: String,
    val manufacturerId: Int,
    val manufacturerData: ByteArray,
    val rssi: Int
) {
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
