package com.muz.bletesting.model

data class BlePreset(
    val title: String,
    val category: String,
    val description: String,
    val manufacturerId: Int = -1,
    val serviceUuid: String? = null,
    val hexData: String
)
