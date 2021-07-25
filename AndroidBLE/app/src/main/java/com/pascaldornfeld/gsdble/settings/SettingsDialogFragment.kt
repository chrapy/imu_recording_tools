package com.pascaldornfeld.gsdble.settings

import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.scan.ScanAdapter
import com.pascaldornfeld.gsdble.scan.ScanDialogFragment
import kotlinx.android.synthetic.main.connect_fragment.view.*

/**
 * fragment to scan for new devices
 */
class SettingsDialogFragment : DialogFragment() {


    /**
     * @param leScanner function returning the ble-scanner
     * @param onUserWantsToConnect the user wants to connect to the bluetooth device

    fun initialize(
        leScanner: () -> (BluetoothLeScanner?),
        onUserWantsToConnect: ((BluetoothDevice) -> Unit),
        isDeviceAlreadyConnected: ((BluetoothDevice) -> Boolean)
    ) {
        this.leScanner = leScanner
        this.onUserWantsToConnect = onUserWantsToConnect
        this.isDeviceNotConnected = isDeviceAlreadyConnected
    }
    */

    private val handler = Handler()

    /**
     * create adapter on start
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.settings_fragment, null).apply {
        }
        return activity?.let {
            AlertDialog.Builder(it)
                .setView(view)
                .setTitle(R.string.scanning)
                .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
                .create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
}