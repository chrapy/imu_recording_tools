package com.pascaldornfeld.gsdble.preprocessing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
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

            //safe the sensors data
            //accelerometer
            var accXAxis = arrayListShortToDouble(it.accData.xAxisData)
            var accYAxis = arrayListShortToDouble(it.accData.yAxisData)
            var accZAxis = arrayListShortToDouble(it.accData.zAxisData)
            var accTotal = getTotalVectorLength(accXAxis, accYAxis, accZAxis)

            var acc = arrayListOf<ArrayList<Double>>(accXAxis, accYAxis, accZAxis, accTotal)

            //gyroscope
            var gyroXAxis = arrayListShortToDouble(it.gyroData.xAxisData)
            var gyroYAxis = arrayListShortToDouble(it.gyroData.yAxisData)
            var gyroZAxis = arrayListShortToDouble(it.gyroData.zAxisData)
            var gyroTotal = getTotalVectorLength(gyroXAxis, gyroYAxis, gyroZAxis)

            var gyro = arrayListOf(gyroXAxis, gyroYAxis, gyroZAxis, gyroTotal)

            //convert the data to meaningful units
            if (sharedPrefs.getBoolean("convertToMU", true)){
                //accelerator data
                accXAxis = convertInMeaningfulUnits(accXAxis, "acc")
                accYAxis = convertInMeaningfulUnits(accYAxis, "acc")
                accZAxis = convertInMeaningfulUnits(accZAxis, "acc")
                accTotal = convertInMeaningfulUnits(accTotal, "acc")

                //gyroscope data
                gyroXAxis = convertInMeaningfulUnits(gyroXAxis, "gyro")
                gyroYAxis = convertInMeaningfulUnits(gyroYAxis, "gyro")
                gyroZAxis = convertInMeaningfulUnits(gyroZAxis, "gyro")
                gyroTotal = convertInMeaningfulUnits(gyroTotal, "gyro")
            }

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


            //safe the data as


            if(sharedPrefs.getBoolean("useFilters", false)) {
                var useThisFilter = sharedPrefs.getString("filters", "none")

                var filteredAccData = arrayListOf<ArrayList<Double>>()
                var filteredGyroData = arrayListOf<ArrayList<Double>>()

                when(useThisFilter){
                    "Mean-Filter" -> {

                        acc.forEach{ data ->
                           filteredAccData.add(meanFilter(data))
                        }

                        /*
                        accXAxis = meanFilter(accXAxis)
                        accYAxis = meanFilter(accYAxis)
                        accZAxis = meanFilter(accZAxis)
                        accTotal = meanFilter(accTotal)

                         */

                        gyro.forEach { data ->
                            filteredGyroData.add(meanFilter(data))
                        }

                    }
                    "Median-Filter" -> {
                        acc.forEach{ data ->
                            filteredAccData.add(medianFilter(data))
                        }

                        gyro.forEach { data ->
                            filteredGyroData.add(medianFilter(data))
                        }
                    }
                    "IIR-Filter" -> {
                        acc.forEach{ data ->
                            filteredAccData.add(iirFilter(data))
                        }

                        gyro.forEach { data ->
                            filteredGyroData.add(iirFilter(data))
                        }
                    }
                    "Kalman-Filter" -> {
                        acc.forEach{ data ->
                            filteredAccData.add(kalmanFilter(data))
                        }

                        gyro.forEach { data ->
                            filteredGyroData.add(kalmanFilter(data))
                        }
                    }
                    else -> {
                        //do nothing
                    }
                }

                acc = filteredAccData
                gyro = filteredGyroData
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



            //safe the preprocessed Data in an PreprocessedSensorData object
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



    /**
     * preprocessing work is done below
     */

    private fun startTSfromZero(timestamps: ArrayList<Long>):ArrayList<Long>{
        var firstTS = timestamps.first()
        var timestampsFromZero = ArrayList<Long>()
        timestamps.forEach{
            timestampsFromZero.add(it-firstTS)
        }

        return timestampsFromZero
    }

    private fun convertInMeaningfulUnits(data:ArrayList<Double>, whichData:String):ArrayList<Double>{

        var convToMeaningfulUnits = ArrayList<Double>()
        var bitDataOutput = 16
        var range = 16
        var signed = true

        if (whichData=="acc"){
            //bitDataOutput
            try {
                bitDataOutput =
                    sharedPrefs.getString("AccOutputSize", "16")
                        .toInt()
            } catch (ex: NumberFormatException) {
                Toast.makeText(
                    context,
                    "Output size must be a number & is set to 16!",
                    Toast.LENGTH_LONG
                ).show()
                sharedPrefs.edit().putString("AccOutputSize", "16").apply()
            }

            //range
            try {
                range =
                    sharedPrefs.getString("AccRange", "16")
                        .toInt()
            } catch (ex: NumberFormatException) {
                Toast.makeText(
                    context,
                    "Range must be a number & is set to 16!",
                    Toast.LENGTH_LONG
                ).show()
                sharedPrefs.edit().putString("AccRange", "16").apply()
            }

            //Acc Signed
            signed = sharedPrefs.getBoolean("AccSigned", true)
        } else {
            if (whichData == "gyro"){
                //bitDataOutput
                try {
                    bitDataOutput =
                        sharedPrefs.getString("GyroOutputSize", "16")
                            .toInt()
                } catch (ex: NumberFormatException) {
                    Toast.makeText(
                        context,
                        "Output size must be a number & is set to 16!",
                        Toast.LENGTH_LONG
                    ).show()
                    sharedPrefs.edit().putString("GyroOutputSize", "16").apply()
                }

                //range
                try {
                    range =
                        sharedPrefs.getString("GyroRange", "2000")
                            .toInt()
                } catch (ex: NumberFormatException) {
                    Toast.makeText(
                        context,
                        "Range must be a number & is set to 2000!",
                        Toast.LENGTH_LONG
                    ).show()
                    sharedPrefs.edit().putString("GyroRange", "2000").apply()
                }

                //Acc Signed
                signed = sharedPrefs.getBoolean("GyroSigned", false)
            }
        }



        var devideRange = 1

        if (signed){
            devideRange = 2
        } else {
            devideRange = 1
        }

        var factor = ((2.0.pow(bitDataOutput.toDouble())/range)/devideRange)

        data.forEach{
            convToMeaningfulUnits.add((it/factor))
        }

        return convToMeaningfulUnits
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


    private fun getTotalVectorLength(xAxis: ArrayList<Double>, yAxis: ArrayList<Double>, zAxis: ArrayList<Double>): ArrayList<Double> {
        var i = 0
        var totalVectorLength = ArrayList<Double>()

        while (i < xAxis.size) {
            totalVectorLength.add(sqrt(
                xAxis[i].pow(2.0) + yAxis[i].pow(2.0) + zAxis[i]
                .pow(2.0)
            ))
            i++
        }
        return totalVectorLength
    }

    private fun arrayListShortToDouble(input:ArrayList<Short>):ArrayList<Double>{
        var output = ArrayList<Double>()

        input.forEach {
            output.add(it.toDouble())
        }

        return output
    }

    /**
     * filters
     */

    private fun meanFilter(data:ArrayList<Double>):ArrayList<Double>{
        //todo: implement
        return data
    }

    private fun medianFilter(data: ArrayList<Double>): ArrayList<Double> {
        //todo: implement
        return data
    }

    private fun kalmanFilter(data: ArrayList<Double>): ArrayList<Double> {
        //todo: implement
        return data
    }

    private fun iirFilter(data: ArrayList<Double>): ArrayList<Double> {
        //todo: implement
        return data
    }


}
