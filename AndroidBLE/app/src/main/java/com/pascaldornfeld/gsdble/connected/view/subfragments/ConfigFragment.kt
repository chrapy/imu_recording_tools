package com.pascaldornfeld.gsdble.connected.view.subfragments

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuConfig

abstract class ConfigFragment<DataType> : Fragment() {
    private var vTitle: TextView? = null
    private var vSlider: SeekBar? = null
    var vCurrent: TextView? = null
    private var vSet: TextView? = null
    private var vApply: Button? = null
    abstract var functionToApply: ((DataType) -> Unit)?

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_config, container, false)
        vTitle = view.findViewById(R.id.tv_title)
        vSlider = view.findViewById(R.id.sk_slider)
        vCurrent = view.findViewById(R.id.tv_current)
        vSet = view.findViewById(R.id.tv_set)
        vApply = view.findViewById(R.id.bt_apply)

        vSlider!!.progress = 0
        vSlider!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                vCurrent!!.text = getStringRepresentationFromData(getDataArray()[progress])
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        vSlider!!.max = getDataArray().size - 1
        vApply!!.setOnClickListener { applyNewData(vSlider!!.progress) }

        return view
    }

    abstract fun getDataArray(): Array<DataType>
    abstract fun getStringRepresentationFromData(data: DataType): String
    protected abstract fun applyNewData(index: Int)

    @SuppressLint("SetTextI18n")
    fun setNewData(data: DataType) {
        val stringRep = getStringRepresentationFromData(data)
        val index = getDataArray().indexOf(data)
        if (index != -1) {
            vSlider!!.progress = index
            vCurrent!!.text = stringRep
        }
        vSet!!.text = stringRep
    }

    fun setTitle(title: String) {
        vTitle!!.text = title
    }
}

class OdrFragment : ConfigFragment<Int>() {
    override var functionToApply: ((Int) -> Unit)? = null

    override fun getDataArray(): Array<Int> = IntArray(7) { it }.toTypedArray()

    override fun getStringRepresentationFromData(data: Int): String =
        ImuConfig.GSDBLE_ODR_INDEX_TO_FREQ[data].toString()

    override fun applyNewData(index: Int) {
        functionToApply?.invoke(index)
    }
}

class PauseFragment : ConfigFragment<Boolean>() {
    override var functionToApply: ((Boolean) -> Unit)? = null

    override fun getDataArray(): Array<Boolean> = arrayOf(false, true)

    override fun getStringRepresentationFromData(data: Boolean): String = data.toString()

    override fun applyNewData(index: Int) {
        functionToApply?.invoke(index == 1)
    }
}

class IntervalFragment : ConfigFragment<Int>() {
    override var functionToApply: ((Int) -> Unit)? = null

    private val prioArray = arrayOf(
        BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER,
        BluetoothGatt.CONNECTION_PRIORITY_BALANCED,
        BluetoothGatt.CONNECTION_PRIORITY_HIGH
    )

    override fun getDataArray(): Array<Int> = prioArray

    override fun getStringRepresentationFromData(data: Int): String = when (data) {
        BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER -> "Low Power"
        BluetoothGatt.CONNECTION_PRIORITY_BALANCED -> "Balanced"
        BluetoothGatt.CONNECTION_PRIORITY_HIGH -> "High"
        else -> "unknown"
    }

    override fun applyNewData(index: Int) {
        functionToApply?.invoke(prioArray[index])
    }
}

class MtuFragment : ConfigFragment<Int>() {
    override var functionToApply: ((Int) -> Unit)? = null
    private val minimum = 23
    private val maximum = 517

    override fun getDataArray(): Array<Int> =
        IntArray(maximum - minimum + 1) { minimum + it }.toTypedArray()

    override fun getStringRepresentationFromData(data: Int): String = data.toString()

    override fun applyNewData(index: Int) {
        functionToApply?.invoke(index + minimum)
    }
}