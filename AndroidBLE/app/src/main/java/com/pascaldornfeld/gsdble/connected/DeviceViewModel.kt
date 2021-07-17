package com.pascaldornfeld.gsdble.connected

import android.app.Application
import android.bluetooth.BluetoothGatt
import androidx.lifecycle.*
import com.pascaldornfeld.gsdble.connected.async_calculations.CharaDataStatsBySecondValue
import com.pascaldornfeld.gsdble.connected.hardware_library.ReadFromDeviceIfc
import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuConfig
import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuData
import com.pascaldornfeld.gsdble.connected.view.DeviceFragment
import com.pascaldornfeld.gsdble.file_dumping.ExtremityData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DeviceViewModel(application: Application, private val deviceName: String) :
    AndroidViewModel(application), ReadFromDeviceIfc {
    val dataAccel = SmallDataHolder<Triple<Short, Short, Short>>(2, 1f)
    val dataGyro = SmallDataHolder<Triple<Short, Short, Short>>(2, 1f)
    val dataDataRate = SmallDataHolder<Long>(3, 1000f)
    val dataDataRateAvg = MutableLiveData<Double>()
    val dataMtu = MutableLiveData<Int>()
    val dataInterval = MutableLiveData<Int>()
    val dataImuConfig = MutableLiveData<ImuConfig>()
    val disconnected = MutableLiveData<Boolean>(false)
    var firstEverRecordedTimestampMs: Long? = null

    init {
        disconnected.observeForever(object : Observer<Boolean?> {
            override fun onChanged(t: Boolean?) {
                if (t != null && t) {
                    clearScheduled.set(true)
                    disconnected.removeObserver(this)
                }
            }
        })
    }

    // ReadFromDeviceIfc

    private val dataRateStatExecutor = Executors.newSingleThreadExecutor()
    private val clearScheduled = AtomicBoolean(false)
    private var currentTrackedSecond = 0L
    private var packetsThisSecond = 0L
    var extremityData: ExtremityData? = null
        set(value) {
            if (value != null) value.deviceMac = deviceName
            field = value
        }

    override fun readImuData(imuData: ImuData) {
        val timeOfPacketArrival = System.currentTimeMillis()

        if (firstEverRecordedTimestampMs == null) firstEverRecordedTimestampMs =
            timeOfPacketArrival - imuData.timeMs

        if (imuData.accel != null) {
            extremityData?.accData?.apply {
                timeStamp.add(imuData.timeMs + firstEverRecordedTimestampMs!!)
                xAxisData.add(imuData.accel.x)
                yAxisData.add(imuData.accel.y)
                zAxisData.add(imuData.accel.z)
            }
            dataAccel.addData(
                imuData.timeMs,
                Triple(imuData.accel.x, imuData.accel.y, imuData.accel.z)
            )
        }

        if (imuData.gyro != null) {
            extremityData?.gyroData?.apply {
                timeStamp.add(imuData.timeMs + firstEverRecordedTimestampMs!!)
                xAxisData.add(imuData.gyro.x)
                yAxisData.add(imuData.gyro.y)
                zAxisData.add(imuData.gyro.z)
            }

            dataGyro.addData(
                imuData.timeMs,
                Triple(imuData.gyro.x, imuData.gyro.y, imuData.gyro.z)
            )
        }

        val thisSecond = timeOfPacketArrival / 1000L
        if (thisSecond != currentTrackedSecond) {
            if (currentTrackedSecond != 0L) {
                val currentDataRateList =
                    dataDataRate.addData(currentTrackedSecond, packetsThisSecond)

                val dataRateData =
                    if (clearScheduled.compareAndSet(true, false)) null
                    else currentDataRateList.toTypedArray()
                CharaDataStatsBySecondValue(dataDataRateAvg::postValue, null)
                    .executeOnExecutor(dataRateStatExecutor, dataRateData)
            }
            currentTrackedSecond = thisSecond
            packetsThisSecond = 0L
        }
        packetsThisSecond++
    }

    override fun afterDisconnect() = disconnected.postValue(true)

    override fun readImuConfig(imuConfig: ImuConfig) = dataImuConfig.postValue(imuConfig)

    override fun readMtu(mtu: Int) = dataMtu.postValue(mtu)

    override fun readConnectionSpeed(interval: Int, latency: Int, timeout: Int) =
        dataInterval.postValue(
            when (interval) {
                in Int.MIN_VALUE..22 -> BluetoothGatt.CONNECTION_PRIORITY_HIGH
                in 23..75 -> BluetoothGatt.CONNECTION_PRIORITY_BALANCED
                else -> BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
            }
        )

    companion object {
        fun forDeviceFragment(deviceFragment: DeviceFragment): DeviceViewModel =
            ViewModelProviders.of(
                deviceFragment,
                DeviceViewModelFactory(
                    deviceFragment.requireActivity().application,
                    deviceFragment.device().address.toString()
                )
            ).get(DeviceViewModel::class.java)
    }
}

class DeviceViewModelFactory(
    private val application: Application,
    private val deviceName: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DeviceViewModel(application, deviceName) as T
    }
}