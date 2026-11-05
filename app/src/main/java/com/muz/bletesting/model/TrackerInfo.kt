package com.muz.bletesting.model

data class TrackerInfo(
    val brand: String,
    val model: String,
    val identifier: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val riskLevel: String,
    val description: String
)
