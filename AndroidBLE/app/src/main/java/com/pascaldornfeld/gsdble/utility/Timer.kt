package com.pascaldornfeld.gsdble.utility

import android.os.Handler
import android.widget.TextView

class Timer(val textView: TextView) {
    private var startTime: Long = 0
    private lateinit var runnable: Runnable

    private val handler = Handler()



    fun start() {
        startTime = System.currentTimeMillis()
        runnable = Runnable {
            var millis: Long = System.currentTimeMillis() - startTime
            var seconds: Int = (millis / 1000).toInt()
            var minutes: Int = seconds / 60
            seconds %= 60
            millis %= 1000
            millis /= 10
            textView.text = String.format("%d:%02d:%02d", minutes, seconds, millis)
            handler.postDelayed(runnable, 100)
        }
        handler.post(runnable)
    }

    fun stop() {
        handler.removeCallbacks(runnable)
    }
}