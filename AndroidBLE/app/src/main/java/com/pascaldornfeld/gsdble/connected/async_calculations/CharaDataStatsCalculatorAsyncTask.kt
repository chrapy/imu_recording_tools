package com.pascaldornfeld.gsdble.connected.async_calculations

import android.os.AsyncTask

/**
 * creates the average and deviation of given data
 *
 * input:
 * Array<Pair<Long,Long>>? Data, or null to clear graph
 *
 * progress:
 * Long? Calculated Average Of Data, Or Null To Clear
 *
 * result:
 * Float? Calculated Deviation Of Data, Or Null To Clear
 */
abstract class CharaDataStatsCalculatorAsyncTask(
    private val averageFunction: ((Double?) -> Unit)?,
    private val deviationFunction: ((Double?) -> Unit)?
) : AsyncTask<Array<Pair<Long, Long>>?, Double?, Double?>() {

    abstract override fun doInBackground(vararg params: Array<Pair<Long, Long>>?): Double?

    override fun onProgressUpdate(vararg values: Double?) {
        super.onProgressUpdate(*values)
        assert(values.isNotEmpty())
        averageFunction?.invoke(values[0])
    }

    override fun onPostExecute(result: Double?) {
        super.onPostExecute(result)
        deviationFunction?.invoke(result)
    }
}