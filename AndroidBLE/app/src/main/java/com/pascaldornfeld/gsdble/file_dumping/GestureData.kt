
package com.pascaldornfeld.gsdble.file_dumping

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/*
This is the object that is going to be written as json
 */
data class GestureData(
    var startTime: String? = null, // startTime
    var endTime: String? = null, // endTime
    val deviceId: String? = null, // smartphone id
    var label: String = "default_label", // gesture class label
    var note: String = "",
    var markedTimeStamps: ArrayList<Long> = ArrayList(), //marked Time stamps of first sensor
    @Suppress("ArrayInDataClass") val datas: Array<ExtremityData> = arrayOf()
) {
    @SuppressLint("HardwareIds")
    constructor(pDatas: Array<ExtremityData>, label: String, context: Context) : this(
        startTime = SimpleDateFormat("yyyy-MM-dd--HH-mm-ss", Locale.US)
            .format(Date(System.currentTimeMillis())),
        datas = pDatas,
        label = label,
        deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    )
}

data class ExtremityData(
    var deviceMac: String = "none",
    var deviceName: String = "none",
    //todo hier noch das selbe mit dem drift
    val accData: SensorData = SensorData(),
    val gyroData: SensorData = SensorData()
    // var timeStampOverflowError: Boolean = false
)

data class SensorData(
    val xAxisData: ArrayList<Short> = ArrayList(),
    val yAxisData: ArrayList<Short> = ArrayList(),
    val zAxisData: ArrayList<Short> = ArrayList(),
    val timeStamp: ArrayList<Long> = ArrayList()
)