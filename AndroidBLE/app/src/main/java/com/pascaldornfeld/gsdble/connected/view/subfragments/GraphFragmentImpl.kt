package com.pascaldornfeld.gsdble.connected.view.subfragments

import android.graphics.Color
import com.androidplot.xy.FastLineAndPointRenderer
import com.androidplot.xy.FastXYSeries
import com.androidplot.xy.RectRegion
import com.androidplot.xy.XYPlot

/**
 * graph to visualize startTime vs 3 sensor axis data (f.e. gyro or accel data)
 */
class SensorGraphFragment : GraphFragment<Triple<Short, Short, Short>>() {
    override fun seriesInit(plot: XYPlot) {
        for (i in 0..2) {
            val axisSeries = object : FastXYSeries {
                override fun getX(index: Int): Number = data[index].first

                override fun getY(index: Int): Number = data[index].second.toList()[i]

                override fun minMax(): RectRegion {
                    val minX = if (data.isEmpty()) 0 else data[0].first
                    val maxX = if (data.isEmpty()) 0 else data[data.lastIndex].first
                    return RectRegion(minX, maxX, Short.MIN_VALUE, Short.MAX_VALUE)
                }

                override fun getTitle(): String {
                    return when {
                        i % 3 == 0 -> "X"
                        i % 3 == 1 -> "Y"
                        else -> "Z"
                    }
                }

                override fun size(): Int = data.size
            }
            val r = if (i % 3 == 0) 255 else 0
            val g = if (i % 3 == 1) 255 else 0
            val b = if (i % 3 == 2) 255 else 0
            plot.addSeries(
                axisSeries,
                FastLineAndPointRenderer.Formatter(Color.rgb(r, g, b), null, null)
            )
        }
    }
}