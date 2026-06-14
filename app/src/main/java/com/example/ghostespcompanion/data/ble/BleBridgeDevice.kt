package com.example.ghostespcompanion.data.ble

data class BleBridgeDevice(
    val address: String,
    val name: String,
    val rssi: Int,
)
