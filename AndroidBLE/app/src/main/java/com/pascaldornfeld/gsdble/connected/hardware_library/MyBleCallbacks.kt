package com.pascaldornfeld.gsdble.connected.hardware_library

import android.bluetooth.BluetoothDevice
import android.util.Log
import no.nordicsemi.android.ble.BleManagerCallbacks

/**
 * callback for bluetooth events happening with gsdble device
 *
 * @param readFromDeviceIfc interface to notify about connection events
 * @param writeToDeviceIfc interface to perform actions (disconnect on error)
 */
internal class MyBleCallbacks(
    private val readFromDeviceIfc: ReadFromDeviceIfc,
    private val writeToDeviceIfc: WriteToDeviceIfc
) : BleManagerCallbacks {

    override fun onDeviceDisconnected(device: BluetoothDevice) =
        readFromDeviceIfc.afterDisconnect()

    // disconnect because of errors

    override fun onDeviceNotSupported(device: BluetoothDevice) {
        Log.w(TAG, "Disconnect because device not supported")
        writeToDeviceIfc.doDisconnect()
    }

    override fun onBondingFailed(device: BluetoothDevice) {
        Log.w(TAG, "Disconnect because bonding failed")
        writeToDeviceIfc.doDisconnect()
    }

    override fun onBondingRequired(device: BluetoothDevice) {
        Log.w(TAG, "Disconnect because bonding required")
        writeToDeviceIfc.doDisconnect()
    }

    override fun onLinkLossOccurred(device: BluetoothDevice) {
        Log.w(TAG, "Disconnect because link loss")
        writeToDeviceIfc.doDisconnect()
    }

    override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
        Log.w(TAG, "Disconnect because error $errorCode")
        writeToDeviceIfc.doDisconnect()
    }

    // ignored

    override fun onDeviceReady(device: BluetoothDevice) = Unit
    override fun onDeviceDisconnecting(device: BluetoothDevice) = Unit
    override fun onDeviceConnected(device: BluetoothDevice) = Unit
    override fun onServicesDiscovered(device: BluetoothDevice, optionalServicesFound: Boolean) =
        Unit

    override fun onBonded(device: BluetoothDevice) = Unit
    override fun onDeviceConnecting(device: BluetoothDevice) = Unit

    companion object {
        private val TAG = MyBleCallbacks::class.java.simpleName.filter { it.isUpperCase() }
    }
}