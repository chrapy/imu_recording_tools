
package com.pascaldornfeld.gsdble.file_dumping

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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

    fun deepCopy() :GestureData {
        val gson = GsonBuilder().create()
        val tmp = gson.toJson(this)
        return gson.fromJson(tmp, GestureData::class.java)
    }
}

data class ExtremityData(
    var deviceMac: String = "none",
    var deviceName: String = "none",
    var deviceDrift: String = "none",
    val accData: SensorData = SensorData(),
    val gyroData: SensorData = SensorData()
    // var timeStampOverflowError: Boolean = false
)

data class SensorData(
    val xAxisData: ArrayList<Short> = ArrayList(),
    val yAxisData: ArrayList<Short> = ArrayList(),
    val zAxisData: ArrayList<Short> = ArrayList(),
    var timeStamp: ArrayList<Long> = ArrayList()
)