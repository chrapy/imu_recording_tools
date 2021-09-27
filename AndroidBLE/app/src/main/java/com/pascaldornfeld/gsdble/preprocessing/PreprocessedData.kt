
package com.pascaldornfeld.gsdble.preprocessing

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.pascaldornfeld.gsdble.file_dumping.ExtremityData
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/*
The Object containing the preprocessed raw GestureData
 */
data class PreprocessedData(
    var startTime: String? = null, // startTime
    var endTime: String? = null, // endTime
    val deviceId: String? = null, // smartphone id
    var label: String = "default_label", // gesture class label
    var note: String = "",
    var markedTimeStamps: ArrayList<Long> = ArrayList(), //marked Time stamps of first sensor
    @Suppress("ArrayInDataClass") val datas: Array<PreprocessedExtremityData> = arrayOf()
)
data class PreprocessedExtremityData(
    var deviceMac: String = "none",
    var deviceName: String = "none",
    var deviceDrift: String = "none",
    val accData: PreprocessedSensorData = PreprocessedSensorData(),
    val gyroData: PreprocessedSensorData = PreprocessedSensorData()
)

data class PreprocessedSensorData(
    val xAxisData: ArrayList<Short> = ArrayList(),
    val yAxisData: ArrayList<Short> = ArrayList(),
    val zAxisData: ArrayList<Short> = ArrayList(),
    val totalVector: ArrayList<Short> = ArrayList(),
    val timeStamp: ArrayList<Long> = ArrayList(),
    val detectedPeaks: DetectedPeaks = DetectedPeaks()
)

data class DetectedPeaks(
    val xPeaks: ArrayList<Short> = ArrayList(),
    val yPeaks: ArrayList<Short> = ArrayList(),
    val zPeaks: ArrayList<Short> = ArrayList(),
    val totalVectorPeaks: ArrayList<Short> = ArrayList()

)