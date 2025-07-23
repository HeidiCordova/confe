package com.example.confe

import java.util.Date

data class ProximityEvent(
    val id: String,
    val localUserId: String,
    val remoteUserId: String,
    val timestamp: Date,
    val rssi: Int,
    val duration: Long
)