package com.pascaldornfeld.gsdble.connected.hardware_library.models

/**
 * Model for configuration characteristic
 *
 * @param odrIndex odr (data collection speed) of the sensor
 * @param paused if sensor is paused
 */
data class ImuConfig(
    val odrIndex: Int,
    val paused: Boolean
) {
    companion object {
        /**
         * array that maps (index) -> (odr in hz) on gsdble device
         */
        val GSDBLE_ODR_INDEX_TO_FREQ = arrayOf(26, 52, 104, 208, 416, 833, 1666)
    }
}