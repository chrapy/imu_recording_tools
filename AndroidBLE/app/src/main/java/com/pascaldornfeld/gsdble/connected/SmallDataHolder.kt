package com.pascaldornfeld.gsdble.connected

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Holds data for GraphFragments that consists of Pair<Time, Data>.
 * When adding data, old data gets removed automatically based on the params
 *
 * @param showSeconds duration in seconds the data should be stored
 * @param timestepScalingMs scaling of the data-timestamp to real-startTime milliseconds
 */
class SmallDataHolder<DataType>(
    private val showSeconds: Int,
    private val timestepScalingMs: Float
) {
    private val data = MutableLiveData<List<Pair<Long, DataType>>>()
    fun getData(): LiveData<List<Pair<Long, DataType>>> = data

    fun addData(p_time: Int, p_data: DataType) = addData(p_time.toLong(), p_data)

    fun addData(p_time: Long, p_data: DataType): List<Pair<Long, DataType>> {
        val oldData = data.value
        val nonemptyDataWithoutOverflow =
            if (oldData == null || oldData.isEmpty() || oldData.first().first > p_time) emptyList() else oldData
        val filteredDataWithNewData =
            nonemptyDataWithoutOverflow.dropWhile { it.first + (((showSeconds * 1000.0f) / timestepScalingMs)).toLong() < p_time }
        return filteredDataWithNewData.plus(Pair(p_time, p_data)).also { data.postValue(it) }
    }

    fun clearData() = data.postValue(emptyList())
}