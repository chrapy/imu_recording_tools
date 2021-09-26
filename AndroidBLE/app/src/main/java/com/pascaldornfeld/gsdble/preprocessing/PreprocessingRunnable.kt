package com.pascaldornfeld.gsdble.preprocessing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.pascaldornfeld.gsdble.MainActivity
import com.pascaldornfeld.gsdble.file_dumping.FileOperations
import com.pascaldornfeld.gsdble.file_dumping.GestureData
import kotlin.math.roundToLong

class PreprocessingRunnable(var recorder: GestureData?, var sharedPrefs: SharedPreferences, var context: Context) : Runnable {


    override fun run() {
        doPreprocessing(recorder)
    }



    //actual Preprocessing methods
    /**
     * organise the preprocessing options
     */
    private fun doPreprocessing(recorder: GestureData?){

        recorder!!.datas.forEach {

            //option to correct the TimeStamps (todo: check preference)

            if(sharedPrefs.getBoolean("recalcWithDrift", false)) {
                var correctedTS = correctTS(it.accData.timeStamp, it.deviceDrift.toFloat())
                it.accData.timeStamp = correctedTS
                it.gyroData.timeStamp = correctedTS
            }



            //option to split timeStamps up (in equally big parts) if multiple datas have the same timestamp (todo: check preference)
            if(sharedPrefs.getBoolean("splitSameTS", false)) {
                var splittedTS = splitTS(it.accData.timeStamp)
                it.accData.timeStamp = splittedTS
                it.gyroData.timeStamp = splittedTS
            }

        }

        //var label = "Ja lol ey"        //recorder.label.toString()  + recorder.startTime.toString() + "_PreProcessed"


        //label = "_PP_"+label+"_PreProcessed"

        var label = "lol"
        recorder.label = "PreProcessed_"

        if(label==null){
            Log.e("label", "label.toString()")
        }else{
            Log.e("label", "NOTNULL")
            Log.e("label", label)
        }


        recorder.let { FileOperations.writeGestureFile(it) }
    }

    private fun correctTS(timestamps: ArrayList<Long>, deviceDrift: Float):ArrayList<Long>{

        Log.e("In", "correctTS")
        var correctedTS = ArrayList<Long>()
        timestamps.forEach{
            correctedTS.add((it * deviceDrift).roundToLong())
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
}