package com.pascaldornfeld.gsdble.connected.hardware_library

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import com.pascaldornfeld.gsdble.BuildConfig
import com.pascaldornfeld.gsdble.MainActivity
import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuConfig
import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuConfig.Companion.GSDBLE_ODR_INDEX_TO_FREQ
import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuData
import com.pascaldornfeld.gsdble.connected.hardware_library.models.Sensor
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.callback.DataSentCallback
import no.nordicsemi.android.ble.data.Data
import java.lang.Integer.min
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Manages a gsdble device. Implements a WriteToDeviceInterface to control the gsdble device.
 * Connects to the device on initialization.
 *
 * @param device The gsdble device to connect to. Obtained from ScanResult after a ble scan
 * @param context A context
 * @param readFromDeviceIfc Implementation of a ReadFromDeviceInterface. Responses from gsdble device are calling its methods
 */
class DeviceManager(
    device: BluetoothDevice,
    context: Context,
    readFromDeviceIfc: ReadFromDeviceIfc,
    startConnectionInterval: Int,
    startImuConfig: ImuConfig
) : WriteToDeviceIfc {
    private val bleManager =
        if (device.name == BuildConfig.GSDBLE_DEVICE_NAME)
            GsdbleBleManager(
                context,
                readFromDeviceIfc,
                this,
                startConnectionInterval,
                startImuConfig
            ).apply { connect(device).enqueue() }
        else ThuBoaBleManager(
            context,
            readFromDeviceIfc,
            this,
            startConnectionInterval,
            startImuConfig
        ).apply { connect(device).enqueue() }

    companion object {
        private val TAG = MainActivity::class.java.simpleName.filter { it.isUpperCase() }
    }

    // WriteToDeviceIfc

    override fun doDisconnect() = bleManager.doDisconnect()

    override fun writeImuConfig(imuConfig: ImuConfig) {
        if (bleManager.isReady) bleManager.writeImuConfig(imuConfig)
    }

    override fun writeMtu(mtu: Int) {
        if (bleManager.isReady) bleManager.writeMtu(mtu)
    }

    override fun writeConnectionPriority(priority: Int) {
        if (bleManager.isReady) bleManager.writeConnectionPriority(priority)
    }

    // BleManager
    private abstract inner class GeneralBleManager(
        context: Context
    ) : BleManager<MyBleCallbacks>(context),
        WriteToDeviceIfc {
        override fun doDisconnect() = disconnect().enqueue()
    }

    private inner class GsdbleBleManager(
        context: Context,
        var readFromDeviceIfc: ReadFromDeviceIfc,
        writeToDeviceIfc: WriteToDeviceIfc,
        startConnectionInterval: Int,
        startImuConfig: ImuConfig
    ) : GeneralBleManager(context) {
        init {
            setGattCallbacks(MyBleCallbacks(readFromDeviceIfc, writeToDeviceIfc))
        }

        // forwarded WriteToDeviceIfc
        override fun writeImuConfig(imuConfig: ImuConfig) =
            writeCharacteristic(charaConfig, imuConfigToByteArray(imuConfig)).enqueue()

        fun imuConfigToByteArray(imuConfig: ImuConfig): ByteArray {
            val byteArray = ByteArray(1)
            byteArray[0] =
                imuConfig.odrIndex.toByte() or (if (imuConfig.paused) 1 else 0).shl(3).toByte()
            return byteArray
        }

        override fun writeMtu(mtu: Int) =
            requestMtu(mtu).with { _, newMtu -> readFromDeviceIfc.readMtu(newMtu) }.enqueue()

        override fun writeConnectionPriority(priority: Int) =
            requestConnectionPriority(priority).with { _, interval, latency, timeout ->
                readFromDeviceIfc.readConnectionSpeed(interval, latency, timeout)
            }.enqueue()

        // BluetoothGattCharacteristics
        private var charaData: BluetoothGattCharacteristic? = null
        private var charaConfig: BluetoothGattCharacteristic? = null

        // BleManagerGattCallback
        override fun getGattCallback(): BleManagerGattCallback = managerGattCallback

        private val managerGattCallback = object : BleManagerGattCallback() {
            private val callbackNewData =
                DataReceivedCallback { _, data ->
                    readFromDeviceIfc.readImuData(
                        ImuData(
                            Sensor(
                                data.getIntValue(Data.FORMAT_SINT16, 0)!!.toShort(),
                                data.getIntValue(Data.FORMAT_SINT16, 2)!!.toShort(),
                                data.getIntValue(Data.FORMAT_SINT16, 4)!!.toShort()
                            ),
                            Sensor(
                                data.getIntValue(Data.FORMAT_SINT16, 6)!!.toShort(),
                                data.getIntValue(Data.FORMAT_SINT16, 8)!!.toShort(),
                                data.getIntValue(Data.FORMAT_SINT16, 10)!!.toShort()
                            ),
                            (data.getIntValue(
                                Data.FORMAT_UINT32,
                                12
                            )!!.toFloat() * 6.4f).toLong()
                        )
                    )
                }
            private val callbackNewConfig =
                DataReceivedCallback { _, data ->
                    readFromDeviceIfc.readImuConfig(
                        ImuConfig(
                            (data.value!![0] and 0x07).toInt(),
                            (data.value!![0] and 0x08).toInt().shr(3) == 1
                        )
                    )
                }

            override fun initialize() {
                super.initialize()
                setNotificationCallback(charaData).with(callbackNewData)
                enableNotifications(charaData).enqueue()

                setNotificationCallback(charaConfig).with(callbackNewConfig)
                readCharacteristic(charaConfig).with(callbackNewConfig).enqueue()
                enableNotifications(charaConfig).enqueue()

                writeMtu(517) // maximum on device
                writeConnectionPriority(startConnectionInterval)
                writeImuConfig(startImuConfig)
            }

            override fun onDeviceDisconnected() {
                charaConfig = null
                charaData = null
            }

            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                val service: BluetoothGattService? =
                    gatt.getService(UUID.fromString(BuildConfig.GSDBLE_UUID_SERVICE))
                charaData =
                    service?.getCharacteristic(UUID.fromString(BuildConfig.GSDBLE_UUID_CHARA_DATA))
                charaConfig =
                    service?.getCharacteristic(UUID.fromString(BuildConfig.GSDBLE_UUID_CHARA_CONFIG))

                val propertiesData = charaData?.properties ?: 0
                val propertiesConfig = charaConfig?.properties ?: 0
                return !(propertiesData and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 ||
                        propertiesConfig and BluetoothGattCharacteristic.PROPERTY_READ == 0 ||
                        propertiesConfig and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 ||
                        propertiesConfig and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0)
            }
        }
    }

    private inner class ThuBoaBleManager(
        context: Context,
        var readFromDeviceIfc: ReadFromDeviceIfc,
        writeToDeviceIfc: WriteToDeviceIfc,
        startConnectionInterval: Int,
        startImuConfig: ImuConfig
    ) : GeneralBleManager(context) {
        init {
            setGattCallbacks(MyBleCallbacks(readFromDeviceIfc, writeToDeviceIfc))
        }

        private val OPCODE_SET_ACC_PERIOD: Byte = 0x04
        private val OPCODE_SET_GYRO_PERIOD: Byte = 0x05

        // forwarded WriteToDeviceIfc
        /**
         * writes imu config.
         * to apply that on thunderboard, we have to push new speed for accel and gyro seperately
         * then we have to reenable notification so new speed gets applied
         */
        override fun writeImuConfig(imuConfig: ImuConfig) {
            val validConfig = ImuConfig(min(imuConfig.odrIndex, 2), false)
            val period =
                (1000f / GSDBLE_ODR_INDEX_TO_FREQ[validConfig.odrIndex]).toInt() + 1 // round up
            val command = byteArrayOf(0, period.toByte(), period.ushr(8).toByte())
            val firstDone = AtomicBoolean(false)
            val whenSent = DataSentCallback { _, _ ->
                if (firstDone.getAndSet(true)) {
                    firstDone.set(false)
                    val whenSent2 = DataSentCallback { _, _ ->
                        if (firstDone.getAndSet(true)) {
                            // we don't really know what is set
                            // so we just call it a success when both requests are sent
                            readFromDeviceIfc.readImuConfig(validConfig)
                        }
                    }
                    enableNotifications(charaAcc).with(whenSent2).enqueue()
                    enableNotifications(charaGyro).with(whenSent2).enqueue()
                }
            }

            writeCharacteristic(
                charaConfig,
                command.copyOf().apply {
                    set(0, OPCODE_SET_ACC_PERIOD)
                }).with(whenSent).enqueue()
            writeCharacteristic(
                charaConfig,
                command.copyOf().apply {
                    set(0, OPCODE_SET_GYRO_PERIOD)
                }).with(whenSent).enqueue()
        }

        override fun writeMtu(mtu: Int) =
            requestMtu(mtu).with { _, newMtu -> readFromDeviceIfc.readMtu(newMtu) }.enqueue()

        override fun writeConnectionPriority(priority: Int) =
            requestConnectionPriority(priority).with { _, interval, latency, timeout ->
                readFromDeviceIfc.readConnectionSpeed(interval, latency, timeout)
            }.enqueue()

        // BluetoothGattCharacteristics
        private var charaAcc: BluetoothGattCharacteristic? = null
        private var charaGyro: BluetoothGattCharacteristic? = null
        private var charaConfig: BluetoothGattCharacteristic? = null

        // BleManagerGattCallback
        override fun getGattCallback(): BleManagerGattCallback = managerGattCallback

        var lastImuData: ImuData? = null

        /**
         * Convert a byte array with a length of 2 to a short.
         */
        private fun byteArrayToShort(byteArray: ByteArray): Short {
            byteArray.reverse()
            return ByteBuffer.wrap(byteArray).short
        }

        private val managerGattCallback = object : BleManagerGattCallback() {
            private val callbackNewAcc = object : DataReceivedCallback {
                override fun onDataReceived(device: BluetoothDevice, data: Data) {
                    val values = data.value
                    synchronized(this) {
                        val localLastImuData = lastImuData
                        if (localLastImuData?.gyro == null) {
                            lastImuData = ImuData(
                                Sensor(
                                    byteArrayToShort(Arrays.copyOfRange(values, 0, 2)),
                                    byteArrayToShort(Arrays.copyOfRange(values, 2, 4)),
                                    byteArrayToShort(Arrays.copyOfRange(values, 4, 6))
                                ),
                                null,
                                System.currentTimeMillis()
                            )
                        } else {
                            readFromDeviceIfc.readImuData(
                                localLastImuData.copy(
                                    accel = Sensor(
                                        byteArrayToShort(Arrays.copyOfRange(values, 0, 2)),
                                        byteArrayToShort(Arrays.copyOfRange(values, 2, 4)),
                                        byteArrayToShort(Arrays.copyOfRange(values, 4, 6))
                                    )
                                )
                            )
                            lastImuData = null
                        }
                    }
                }
            }

            private val callbackNewGyro = object : DataReceivedCallback {
                override fun onDataReceived(device: BluetoothDevice, data: Data) {
                    val values = data.value
                    synchronized(this) {
                        val localLastImuData = lastImuData
                        if (localLastImuData?.accel == null) {
                            lastImuData = ImuData(
                                null,
                                Sensor(
                                    byteArrayToShort(Arrays.copyOfRange(values, 0, 2)),
                                    byteArrayToShort(Arrays.copyOfRange(values, 2, 4)),
                                    byteArrayToShort(Arrays.copyOfRange(values, 4, 6))
                                ),
                                System.currentTimeMillis()
                            )
                        } else {
                            readFromDeviceIfc.readImuData(
                                localLastImuData.copy(
                                    gyro = Sensor(
                                        byteArrayToShort(Arrays.copyOfRange(values, 0, 2)),
                                        byteArrayToShort(Arrays.copyOfRange(values, 2, 4)),
                                        byteArrayToShort(Arrays.copyOfRange(values, 4, 6))
                                    )
                                )
                            )
                            lastImuData = null
                        }
                    }
                }
            }

            private val callbackNewConfig = object : DataReceivedCallback {
                override fun onDataReceived(device: BluetoothDevice, data: Data) {
                    Log.w(TAG, "new config received: $device, $data")
                }
            }

            override fun initialize() {
                super.initialize()
                setNotificationCallback(charaAcc).with(callbackNewAcc)
                enableNotifications(charaAcc).enqueue()

                setNotificationCallback(charaGyro).with(callbackNewGyro)
                enableNotifications(charaGyro).enqueue()

                setNotificationCallback(charaConfig).with(callbackNewConfig)
                enableNotifications(charaConfig).enqueue()

                writeMtu(517) // maximum on device
                writeConnectionPriority(startConnectionInterval)
                writeImuConfig(startImuConfig)
            }

            override fun onDeviceDisconnected() {
                charaConfig = null
                charaAcc = null
                charaGyro = null
            }

            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                val service: BluetoothGattService? =
                    gatt.getService(UUID.fromString(BuildConfig.THUBOA_UUID_SERVICE))
                charaAcc =
                    service?.getCharacteristic(UUID.fromString(BuildConfig.THUBOA_UUID_CHARA_ACC))
                charaGyro =
                    service?.getCharacteristic(UUID.fromString(BuildConfig.THUBOA_UUID_CHARA_GYRO))
                charaConfig =
                    service?.getCharacteristic(UUID.fromString(BuildConfig.THUBOA_UUID_CHARA_CONFIG))

                val propertiesAcc = charaAcc?.properties ?: 0
                val propertiesGyro = charaGyro?.properties ?: 0
                val propertiesConfig = charaConfig?.properties ?: 0
                return !(propertiesAcc and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 ||
                        propertiesGyro and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 ||
                        propertiesConfig and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 ||
                        propertiesConfig and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0)
            }
        }
    }
}