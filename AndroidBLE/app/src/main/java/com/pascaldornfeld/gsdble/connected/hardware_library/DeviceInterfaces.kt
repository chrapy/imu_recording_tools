package com.pascaldornfeld.gsdble.connected.hardware_library

import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuConfig
import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuData
import java.io.Serializable


/**
 * Interface To Read Events From Device
 */
interface ReadFromDeviceIfc {
    fun afterDisconnect()
    fun readImuData(imuData: ImuData)
    fun readImuConfig(imuConfig: ImuConfig)
    fun readMtu(mtu: Int)
    fun readConnectionSpeed(interval: Int, latency: Int, timeout: Int)
}

/**
 * Interface To Write Events To Device
 */
interface WriteToDeviceIfc : Serializable {
    fun doDisconnect()
    fun writeImuConfig(imuConfig: ImuConfig)
    fun writeMtu(mtu: Int)
    fun writeConnectionPriority(priority: Int)
}