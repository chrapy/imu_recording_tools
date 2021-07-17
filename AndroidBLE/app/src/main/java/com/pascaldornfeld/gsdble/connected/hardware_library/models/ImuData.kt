package com.pascaldornfeld.gsdble.connected.hardware_library.models

/**
 * Model for data characteristic
 *
 * @param accel data of accelerometer, if available
 * @param gyro data of gyroscope, if available
 * @param timeMs sensor startTime in ms
 */
data class ImuData(
    val accel: Sensor?,
    val gyro: Sensor?,
    val timeMs: Long
)

data class Sensor(
    val x: Short,
    val y: Short,
    val z: Short
)