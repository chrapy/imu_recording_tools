package com.pascaldornfeld.gsdble.preprocessing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.pascaldornfeld.gsdble.file_dumping.FileOperations
import com.pascaldornfeld.gsdble.file_dumping.GestureData
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

class PreprocessingRunnable(var recorder: GestureData?, var sharedPrefs: SharedPreferences, var context: Context) : Runnable {


    override fun run() {
        doPreprocessing(recorder)
    }



    //actual Preprocessing methods
    /**
     * organise the preprocessing options
     */
    private fun doPreprocessing(recorder: GestureData?){


        val preprocessedDataArray = ArrayList<PreprocessedExtremityData>()

        recorder!!.datas.forEach {


            //gather the extremityData that will be preprocessed
            //set the first TS as 0 and calculate the rest relative to 0
            var timestamps = startTSfromZero(it.accData.timeStamp)


            //todo einheiten umrechnen

            //safe the sensors data
            //accelerometer
            var accXAxis = it.accData.xAxisData
            var accYAxis = it.accData.yAxisData
            var accZAxis = it.accData.zAxisData
            var accTotal = getTotalVectorLength(accXAxis, accYAxis, accZAxis)

            //gyroscope
            var gyroXAxis = it.gyroData.xAxisData
            var gyroYAxis = it.gyroData.yAxisData
            var gyroZAxis = it.gyroData.zAxisData
            var gyroTotal = getTotalVectorLength(gyroXAxis, gyroYAxis, gyroZAxis)




            //option to correct the TimeStamps
            if(sharedPrefs.getBoolean("recalcWithDrift", false)) {
                var correctedTS = correctTS(timestamps, it.deviceDrift.toFloat())
                Log.e("correctTS", correctedTS.toString())
                timestamps=correctedTS
            }



            //option to split timeStamps up (in equally big parts) if multiple datas have the same timestamp
            if(sharedPrefs.getBoolean("splitSameTS", false)) {
                var splittedTS = splitTS(timestamps)
                Log.e("splitTS", splittedTS.toString())
                timestamps=splittedTS
            }

            //todo FIlters

            //todo mean-filter
            //todo median-filter
            //todo Frequenzbandfilter
            //todo kalman filter

            //peakDetection
            var detectedAccPeaks = DetectedPeaks()
            var detectedGyroPeaks = DetectedPeaks()

            //todo if peak detection then get Acc/Gyro Peaks and safe the timestamps in respective DetectedPeaks
            //todo: evtl unterschiedliche PeakDetection Funktionen zur auswahl stellen



            //safe the preprocessed Data in an ExtremityData object
            var accData = PreprocessedSensorData(accXAxis, accYAxis, accZAxis, accTotal, timestamps, detectedAccPeaks)

            var gyroData = PreprocessedSensorData(gyroXAxis, gyroYAxis, gyroZAxis, gyroTotal, timestamps, detectedGyroPeaks)

            var preprocessedData = PreprocessedExtremityData(it.deviceMac, it.deviceName, it.deviceDrift, accData, gyroData)


            //add the ExtremityData to the ExtremityData Array
            preprocessedDataArray.add(preprocessedData)
        }





        //safe data as 'PreprocessedData'

        var preprocessed = PreprocessedData(recorder.startTime, recorder.endTime, recorder.deviceId, recorder.label, recorder.note, recorder.markedTimeStamps, preprocessedDataArray.toTypedArray())

        preprocessed.let { FileOperations.writePreprocessedFile(it) }
    }

    private fun startTSfromZero(timestamps: ArrayList<Long>):ArrayList<Long>{
        var firstTS = timestamps.first()
        var timestampsFromZero = ArrayList<Long>()
        timestamps.forEach{
            timestampsFromZero.add(it-firstTS)
        }

        return timestampsFromZero
    }

    private fun correctTS(timestamps: ArrayList<Long>, deviceDrift: Float):ArrayList<Long>{

        Log.e("In", "correctTS")
        Log.e("drift", deviceDrift.toString())
        var correctedTS = ArrayList<Long>()
        timestamps.forEach{
            var corrected = it*deviceDrift
            correctedTS.add(corrected.roundToLong())
        }

        return correctedTS
    }

    private fun splitTS(timestamps: ArrayList<Long>):ArrayList<Long>{

        Log.e("In", "splitTS")

        var splittedTS = timestamps
        var sameValue:Int = 0
        var startValuePosition:Int = 0
        var i:Int = 0

        while(i<timestamps.size){
            if(timestamps[startValuePosition]==timestamps[i]){
                sameValue += 1
                i += 1
            } else{
                if (sameValue>0){
                    var j = 1
                    while (j<sameValue){
                        splittedTS[startValuePosition + j] = timestamps[(startValuePosition + j) - 1] + (timestamps[startValuePosition + sameValue]-timestamps[startValuePosition])/sameValue
                        j+=1
                    }
                    startValuePosition += sameValue
                    sameValue = 0
                }
            }
        }

        return splittedTS
    }


    private fun getTotalVectorLength(xAxis: ArrayList<Short>, yAxis: ArrayList<Short>, zAxis: ArrayList<Short>): ArrayList<Short> {
        var i = 0
        var totalVectorLength = ArrayList<Short>()

        while (i < xAxis.size) {
            totalVectorLength.add(sqrt(xAxis[i].toDouble().pow(2.0) + yAxis[i].toDouble().pow(2.0) + zAxis[i].toDouble()
                .pow(2.0)
            ).toInt().toShort())
            i++
        }
        return totalVectorLength
    }
}
