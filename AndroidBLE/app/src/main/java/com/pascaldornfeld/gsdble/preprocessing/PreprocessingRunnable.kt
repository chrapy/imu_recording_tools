package com.pascaldornfeld.gsdble.preprocessing

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.pascaldornfeld.gsdble.file_dumping.FileOperations
import com.pascaldornfeld.gsdble.file_dumping.GestureData
import uk.me.berndporr.iirj.Bessel
import uk.me.berndporr.iirj.Butterworth
import uk.me.berndporr.iirj.ChebyshevI
import uk.me.berndporr.iirj.ChebyshevII
import kotlin.collections.ArrayList
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt


class PreprocessingRunnable(
    var recorder: GestureData?,
    var sharedPrefs: SharedPreferences,
    var context: Context
) : Runnable {


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
                acc[0] = convertInMeaningfulUnits(acc[0], "acc")
                acc[1] = convertInMeaningfulUnits(acc[1], "acc")
                acc[2] = convertInMeaningfulUnits(acc[2], "acc")
                acc[3] = convertInMeaningfulUnits(acc[3], "acc")

                //gyroscope data
                gyro[0] = convertInMeaningfulUnits(gyro[0], "gyro")
                gyro[1] = convertInMeaningfulUnits(gyro[1], "gyro")
                gyro[2] = convertInMeaningfulUnits(gyro[2], "gyro")
                gyro[3] = convertInMeaningfulUnits(gyro[3], "gyro")
            }

            //option to correct the TimeStamps
            if(sharedPrefs.getBoolean("recalcWithDrift", false)) {
                var correctedTS = correctTS(timestamps, it.deviceDrift.toFloat())
                timestamps=correctedTS
            }


            //option to split timeStamps up (in equally big parts) if multiple datas have the same timestamp
            if(sharedPrefs.getBoolean("splitSameTS", false)) {
                var splittedTS = splitTS(timestamps)
                timestamps=splittedTS
            }


            //safe the data as


            if(sharedPrefs.getBoolean("useFilters", false)) {
                var useThisFilter = sharedPrefs.getString("filters", "none")
                var samplingRate = (timestamps.size.toDouble()/(timestamps.last().toDouble() - timestamps.first().toDouble()))*1000 //sampling rate in Hz
                
                var windowsize=0
                try {
                    windowsize =
                        (sharedPrefs!!.getString("windowsize", "0")
                            .toInt())/2
                } catch (ex: NumberFormatException) {

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Windowsize must be an Integer & is set to 0!", Toast.LENGTH_SHORT).show()
                    }


                    sharedPrefs.edit().putString("windowsize", "0").apply()
                }

                var order = 0         //int
                try {
                    order =
                        sharedPrefs!!.getString("order", "1")
                            .toInt()
                } catch (ex: NumberFormatException) {

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Order must be an Integer & is set to 0!", Toast.LENGTH_SHORT).show()
                    }


                    sharedPrefs.edit().putString("order", "0").apply()
                }


                var centerFreq = 1.0
                try {
                    centerFreq =
                        sharedPrefs!!.getString("centerFreq", "1.0")
                            .toDouble()

                    //todo checken ob das to Double klappt!!!
                    Log.e("toDouble", centerFreq.toString())
                } catch (ex: NumberFormatException) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "The center frequency must be a Double & is set to 1.0!", Toast.LENGTH_SHORT).show()
                    }
                    sharedPrefs.edit().putString("centerFreq", "1.0").apply()
                }


                var widthFreq = 1.0
                try {
                    widthFreq =
                        sharedPrefs!!.getString("widthFreq", "1.0")
                            .toDouble()

                } catch (ex: NumberFormatException) {

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "The width frequency must be a Double & is set to 1.0!", Toast.LENGTH_SHORT).show()
                    }

                    sharedPrefs.edit().putString("widthFreq", "1.0").apply()
                }


                var cutOffFreq = 1.0
                try {
                    cutOffFreq =
                        sharedPrefs!!.getString("cutOffFreq", "1.0")
                            .toDouble()

                } catch (ex: NumberFormatException) {

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "The cutoff frequency must be a Double & is set to 1.0!", Toast.LENGTH_SHORT).show()
                    }


                    sharedPrefs.edit().putString("cutOffFreq", "1.0").apply()
                }



                var ripple = 1.0
                try {
                    ripple =
                        sharedPrefs!!.getString("ripple", "1.0")
                            .toDouble()

                } catch (ex: NumberFormatException) {

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Ripple must be a Double & is set to 1.0!", Toast.LENGTH_SHORT).show()
                    }

                    sharedPrefs.edit().putString("ripple", "1.0").apply()
                }



                var filteredAccData = arrayListOf<ArrayList<Double>>()
                var filteredGyroData = arrayListOf<ArrayList<Double>>()

                when(useThisFilter){
                    "Mean-Filter" -> {

                        acc.forEach { data ->
                            filteredAccData.add(meanFilter(data, windowsize))
                        }


                        gyro.forEach { data ->
                            filteredGyroData.add(meanFilter(data, windowsize))
                        }

                    }
                    "Median-Filter" -> {

                        acc.forEach { data ->
                            filteredAccData.add(medianFilter(data, windowsize))
                        }

                        gyro.forEach { data ->
                            filteredGyroData.add(medianFilter(data, windowsize))
                        }
                    }
                    "Butterworth-Filter" -> {

                        acc.forEach { data ->
                            filteredAccData.add(butterworthFilter(data, samplingRate, order, cutOffFreq, centerFreq, widthFreq))
                        }

                        gyro.forEach { data ->
                            filteredGyroData.add(butterworthFilter(data, samplingRate, order, cutOffFreq, centerFreq, widthFreq))
                        }
                    }
                    "Bessel-Filter" -> {
                        acc.forEach { data ->
                            filteredAccData.add(besselFilter(data, samplingRate, order, cutOffFreq, centerFreq, widthFreq))
                        }

                        gyro.forEach { data ->
                            filteredGyroData.add(besselFilter(data, samplingRate, order, cutOffFreq, centerFreq, widthFreq))
                        }
                    }
                    "ChebyshevI-Filter" -> {
                        acc.forEach { data ->
                            filteredAccData.add(chebyshevIFilter(data, samplingRate, order, cutOffFreq, centerFreq, widthFreq, ripple))
                        }

                        gyro.forEach { data ->
                            filteredGyroData.add(chebyshevIFilter(data, samplingRate, order, cutOffFreq, centerFreq, widthFreq, ripple))
                        }
                    }
                    "ChebyshevII-Filter" -> {
                        acc.forEach { data ->
                            filteredAccData.add(chebyshevIIFilter(data, samplingRate, order, cutOffFreq, centerFreq, widthFreq, ripple))
                        }

                        gyro.forEach { data ->
                            filteredGyroData.add(chebyshevIIFilter(data, samplingRate, order, cutOffFreq, centerFreq, widthFreq, ripple))
                        }
                    }
                    else -> {
                        filteredAccData = acc
                        filteredGyroData = gyro
                    }
                }

                acc = filteredAccData
                gyro = filteredGyroData
            }




            Log.i("Acc: ", acc.toString())
            //safe the preprocessed Data in an PreprocessedSensorData object
            var accData = PreprocessedSensorData(
                acc[0],
                acc[1],
                acc[2],
                acc[3],
                timestamps
            )

            Log.i("Gyro: ", gyro.toString())
            var gyroData = PreprocessedSensorData(
                gyro[0],
                gyro[1],
                gyro[2],
                gyro[3],
                timestamps
            )


            var preprocessedData = PreprocessedExtremityData(
                it.deviceMac,
                it.deviceName,
                it.deviceDrift,
                accData,
                gyroData
            )

            //add the ExtremityData to the ExtremityData Array
            preprocessedDataArray.add(preprocessedData)
        }





        //safe data as 'PreprocessedData'

        var preprocessed = PreprocessedData(
            recorder.startTime,
            recorder.endTime,
            recorder.deviceId,
            recorder.label,
            recorder.note,
            recorder.markedTimeStamps,
            preprocessedDataArray.toTypedArray()
        )

        preprocessed.let { FileOperations.writePreprocessedFile(it) }
    }



    /**
     * preprocessing work is done below
     */

    private fun startTSfromZero(timestamps: ArrayList<Long>):ArrayList<Long>{
        var firstTS = timestamps.first()
        var timestampsFromZero = ArrayList<Long>()
        timestamps.forEach{
            timestampsFromZero.add(it - firstTS)
        }

        return timestampsFromZero
    }

    private fun convertInMeaningfulUnits(data: ArrayList<Double>, whichData: String):ArrayList<Double>{

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


                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Output size must be a number & is set to 16!", Toast.LENGTH_SHORT).show()
                }

                sharedPrefs.edit().putString("AccOutputSize", "16").apply()
            }

            //range
            try {
                range =
                    sharedPrefs.getString("AccRange", "16")
                        .toInt()
            } catch (ex: NumberFormatException) {

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Range must be a number & is set to 16!", Toast.LENGTH_SHORT).show()
                }


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

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Output size must be a number & is set to 16!", Toast.LENGTH_SHORT).show()
                    }

                    sharedPrefs.edit().putString("GyroOutputSize", "16").apply()
                }

                //range
                try {
                    range =
                        sharedPrefs.getString("GyroRange", "2000")
                            .toInt()
                } catch (ex: NumberFormatException) {

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Range must be a number & is set to 2000!", Toast.LENGTH_SHORT).show()
                    }


                    sharedPrefs.edit().putString("GyroRange", "2000").apply()
                }

                //Acc Signed
                signed = sharedPrefs.getBoolean("GyroSigned", false)
            }
        }


        var devideRange: Int

        if (signed){
            devideRange = 2
        } else {
            devideRange = 1
        }

        var factor = ((2.0.pow(bitDataOutput.toDouble())/range)/devideRange)

        data.forEach{
            convToMeaningfulUnits.add((it / factor))
        }

        return convToMeaningfulUnits
    }

    private fun correctTS(timestamps: ArrayList<Long>, deviceDrift: Float):ArrayList<Long>{

        var correctedTS = ArrayList<Long>()
        timestamps.forEach{
            var corrected = it*deviceDrift
            correctedTS.add(corrected.roundToLong())
        }

        return correctedTS
    }

    private fun splitTS(timestamps: ArrayList<Long>):ArrayList<Long>{


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


    private fun getTotalVectorLength(
        xAxis: ArrayList<Double>,
        yAxis: ArrayList<Double>,
        zAxis: ArrayList<Double>
    ): ArrayList<Double> {
        var i = 0
        var totalVectorLength = ArrayList<Double>()

        while (i < xAxis.size) {
            totalVectorLength.add(
                sqrt(
                    xAxis[i].pow(2.0) + yAxis[i].pow(2.0) + zAxis[i]
                        .pow(2.0)
                )
            )
            i++
        }
        return totalVectorLength
    }

    private fun arrayListShortToDouble(input: ArrayList<Short>):ArrayList<Double>{
        var output = ArrayList<Double>()

        input.forEach {
            output.add(it.toDouble())
        }

        return output
    }

    /**
     * filters
     */

    private fun meanFilter(data: ArrayList<Double>, n: Int):ArrayList<Double>{

        Log.i("Mean", "!")

        var filteredData = ArrayList<Double>()

        var i = 0

        while (i<data.size){
            var mean = 0.0

            var j = i
            var sum = 0.0
            var noOfElement = 1 //there is at least the element itself in the list

            //get the n values in front of i
            if (j-n < 0){
                j=0
            } else {
                j-=n
            }

            while (j<i){
              sum+=data[j]
              noOfElement +=1
              j++
            }

            //get the n values behind i
            j = i

            if(j+n>=data.size){
                j=data.size-1
            } else {
                j+=n
            }

            while (j>i){
                sum+=data[j]
                noOfElement +=1
                j--
            }

            sum += data[i]
            noOfElement += 1

            mean = sum/noOfElement

            i++
            filteredData.add(mean)
        }
        return filteredData
    }


    private fun medianFilter(data: ArrayList<Double>, n: Int): ArrayList<Double> {

        Log.i("Median", "!")
        var filteredData = ArrayList<Double>()

        var i = 0

        while (i<data.size){
            var median = 0.0

            var j = i
            var elements = ArrayList<Double>()
            var noOfElement = 1 //there is at least the element itself in the list

            //get the n values in front of i
            if (j-n < 0){
                j=0
            } else {
                j-=n
            }

            while (j<i){
                elements.add(data[j])
                noOfElement +=1
                j++
            }

            //get the n values behind i
            j = i

            if(j+n>=data.size){
                j=data.size-1
            } else {
                j+=n
            }

            while (j>i){
                elements.add(data[j])
                noOfElement +=1
                j--
            }

            elements.sort()


            if (noOfElement%2!=0){
                median = elements[noOfElement/2]
            }else{
                var o = elements[(noOfElement/2)-1]
                var l = elements[noOfElement/2]

                median = (o+l)/2
            }

            i++
            filteredData.add(median)
        }
        return filteredData
    }


    private fun butterworthFilter(data: ArrayList<Double>, samplingRate: Double, order: Int, cutOffFreq: Double, centerFreq:Double, widthFreq:Double): ArrayList<Double> {

        var whichFilter = sharedPrefs.getString("iirfilters", "none")

        var butterworth = Butterworth()
        Log.i("Butterworth", "!")

        var filteredData = ArrayList<Double>()
        when(whichFilter){
            "Lowpass" -> {
                Log.i("Butterworth", "Lowpass")
                butterworth.lowPass(order, samplingRate, cutOffFreq)
            }

            "Highpass" -> {
                Log.i("Butterworth", "Highpass")
                butterworth.highPass(order, samplingRate, cutOffFreq)
            }

            "Bandpass" -> {
                Log.i("Butterworth", "Bandpass")
                butterworth.bandPass(order, samplingRate, centerFreq, widthFreq)
            }

            "Bandstop" -> {
                Log.i("Butterworth", "Bandstop")
                butterworth.bandStop(order, samplingRate, centerFreq, widthFreq)
            }

            else -> {
                //do nothing
            }
        }

        data.forEach { d ->
            var v = butterworth.filter(d)
            filteredData.add(v)
        }
        return filteredData
    }

    private fun besselFilter(data: ArrayList<Double>, samplingRate: Double, order: Int, cutOffFreq: Double, centerFreq:Double, widthFreq:Double): ArrayList<Double> {


        var whichFilter = sharedPrefs.getString("iirfilters", "none")

        Log.i("Bessel", "!")

        var bessel = Bessel()

        var filteredData = ArrayList<Double>()
        when(whichFilter){
            "Lowpass" -> {
                bessel.lowPass(order, samplingRate, cutOffFreq)
                Log.i("Bessel", "Lowpass")
            }

            "Highpass" -> {
                bessel.highPass(order, samplingRate, cutOffFreq)
                Log.i("Bessel", "Highpass")
            }

            "Bandpass" -> {
                bessel.bandPass(order, samplingRate, centerFreq, widthFreq)
                Log.i("Bessel", "Bandpass")
            }

            "Bandstop" -> {
                bessel.bandStop(order, samplingRate, centerFreq, widthFreq)
                Log.i("Bessel", "Bandstop")
            }

            else -> {
                //do nothing
            }
        }

        data.forEach { d ->
            var v = bessel.filter(d)
            filteredData.add(v)
        }
        return filteredData

    }

    private fun chebyshevIFilter(data: ArrayList<Double>, samplingRate: Double, order: Int, cutOffFreq: Double, centerFreq:Double, widthFreq:Double, ripple:Double): ArrayList<Double> {

        var whichFilter = sharedPrefs.getString("iirfilters", "none")

        Log.i("CH", "!")

        var ch = ChebyshevI()

        var filteredData = ArrayList<Double>()
        when(whichFilter){
            "Lowpass" -> {
                ch.lowPass(order, samplingRate, cutOffFreq, ripple)
                Log.i("CH", "Lowpass")
            }

            "Highpass" -> {
                ch.highPass(order, samplingRate, cutOffFreq, ripple)
                Log.i("CH", "Highpass")
            }

            "Bandpass" -> {
                ch.bandPass(order, samplingRate, centerFreq, widthFreq, ripple)
                Log.i("CH", "Bandpass")
            }

            "Bandstop" -> {
                ch.bandStop(order, samplingRate, centerFreq, widthFreq, ripple)

                Log.i("CH", "Bandstop")
            }

            else -> {
                //do nothing
            }
        }

        data.forEach { d ->
            var v = ch.filter(d)
            filteredData.add(v)
        }
        return filteredData

    }

    private fun chebyshevIIFilter(data: ArrayList<Double>, samplingRate: Double, order: Int, cutOffFreq: Double, centerFreq:Double, widthFreq:Double, ripple:Double): ArrayList<Double> {
        var whichFilter = sharedPrefs.getString("iirfilters", "none")

        Log.i("CH2", "!")

        var ch2 = ChebyshevII()

        var filteredData = ArrayList<Double>()
        when(whichFilter){
            "Lowpass" -> {
                ch2.lowPass(order, samplingRate, cutOffFreq, ripple)
                Log.i("CH2", "Lowpass")
            }

            "Highpass" -> {
                ch2.highPass(order, samplingRate, cutOffFreq, ripple)
                Log.i("CH2", "Highpass")
            }

            "Bandpass" -> {
                ch2.bandPass(order, samplingRate, centerFreq, widthFreq, ripple)
                Log.i("CH2", "Bandpass")
            }

            "Bandstop" -> {
                ch2.bandStop(order, samplingRate, centerFreq, widthFreq, ripple)

                Log.i("CH2", "Bandstop")
            }

            else -> {
                return data
            }
        }

        data.forEach { d ->
            var v = ch2.filter(d)
            filteredData.add(v)
        }
        return filteredData

    }


    //todo Debuggen (schauen ob die in der Liste ausgewählten Filter auch gestartet werden

}
