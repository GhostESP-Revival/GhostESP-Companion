package com.example.ghostespcompanion.data.ble

import java.util.UUID

object BleBridgeConstants {
    val SERVICE_UUID: UUID = UUID.fromString("47686f73-7445-5350-4272-696467655376")
    val RX_UUID: UUID = UUID.fromString("0147686f-7374-4553-5042-726964675852")
    val TX_UUID: UUID = UUID.fromString("0247686f-7374-4553-5042-726964675854")
    val CTRL_UUID: UUID = UUID.fromString("0347686f-7374-4553-5042-72694c525443")
    val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
