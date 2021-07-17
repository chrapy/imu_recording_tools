package com.pascaldornfeld.gsdble.connected.view.subfragments

import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.androidplot.Plot
import com.androidplot.PlotListener
import com.androidplot.SeriesRegistry
import com.androidplot.ui.Formatter
import com.androidplot.ui.SeriesBundle
import com.androidplot.ui.SeriesRenderer
import com.androidplot.util.PixelUtils
import com.androidplot.xy.XYGraphWidget
import com.androidplot.xy.XYPlot
import com.pascaldornfeld.gsdble.R
import kotlinx.android.synthetic.main.fragment_graph.view.*
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition
import java.util.concurrent.atomic.AtomicBoolean

abstract class GraphFragment<DataType> : Fragment() {
    private val canUpdate = AtomicBoolean(true) // if data can be updated
    protected var data = listOf<Pair<Long, DataType>>() // snapshots of internalData for vPlot

    /** How to print values of DataType on the axis. Default implementation prints as Integer */
    open fun formatterY(obj: Number, toAppendTo: StringBuffer): StringBuffer =
        toAppendTo.append(obj.toLong())

    fun setTitle(title: String) {
        view?.vPlotGraph?.setTitle(title)
    }

    /** add data series in this method */
    abstract fun seriesInit(plot: XYPlot)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_graph, container, false)

        // init plotting graph
        seriesInit(view.vPlotGraph.apply {
            graph.apply {
                getLineLabelStyle(XYGraphWidget.Edge.LEFT).apply {
                    paint.textSize = PixelUtils.spToPix(9.0f)
                    format = object : Format() {
                        override fun format(
                            obj: Any,
                            toAppendTo: StringBuffer,
                            pos: FieldPosition
                        ): StringBuffer = formatterY(obj as Number, toAppendTo)

                        override fun parseObject(source: String, pos: ParsePosition): Any? = null
                    }
                }
                getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).apply {
                    paint.textSize = PixelUtils.spToPix(9.0f)
                    format = object : Format() {
                        override fun format(
                            obj: Any,
                            toAppendTo: StringBuffer,
                            pos: FieldPosition
                        ): StringBuffer = toAppendTo.append((obj as Number).toLong())

                        override fun parseObject(source: String, pos: ParsePosition): Any? = null
                    }
                }
            }
            addListener(object : PlotListener {
                override fun onAfterDraw(
                    source: Plot<*, out Formatter<*>, out SeriesRenderer<*, *, *>, out SeriesBundle<*, *>, out SeriesRegistry<*, *, *>>?,
                    canvas: Canvas?
                ) = canUpdate.set(true)

                override fun onBeforeDraw(
                    source: Plot<*, out Formatter<*>, out SeriesRenderer<*, *, *>, out SeriesBundle<*, *>, out SeriesRegistry<*, *, *>>?,
                    canvas: Canvas?
                ) = canUpdate.set(false)

            })
            visibility = GONE
        })
        return view
    }

    fun showGraph(show: Boolean) {
            view?.vPlotGraph?.visibility = if (show) VISIBLE else GONE
    }

    fun updateData(newDataList: List<Pair<Long, DataType>>) {
        val graph = view?.vPlotGraph
        if (isResumed
            && graph != null
            && graph.isVisible
            && canUpdate.compareAndSet(true, false)
        ) {
            data = newDataList.toList()
            graph.redraw()
        }
    }
}