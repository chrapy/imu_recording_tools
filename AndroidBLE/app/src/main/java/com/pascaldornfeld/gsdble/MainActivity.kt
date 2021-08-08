package com.pascaldornfeld.gsdble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.pascaldornfeld.gsdble.connected.DeviceViewModel
import com.pascaldornfeld.gsdble.connected.hardware_library.DeviceManager
import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuConfig
import com.pascaldornfeld.gsdble.connected.view.DeviceFragment
import com.pascaldornfeld.gsdble.file_dumping.ExtremityData
import com.pascaldornfeld.gsdble.file_dumping.FileOperations
import com.pascaldornfeld.gsdble.file_dumping.GestureData
import com.pascaldornfeld.gsdble.scan.ScanDialogFragment
import kotlinx.android.synthetic.main.main_activity.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity(), DeviceFragment.RemovableDeviceActivity,
    NumberPicker.OnValueChangeListener {
    private var bleReady = true
    private val bluetoothAdapter: BluetoothAdapter? by lazy { (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter }
    private lateinit var connectDialog: ScanDialogFragment

    private var recorder: GestureData? = null
    private lateinit var recLabel:String
    private lateinit var labelTextView : TextView
    private var isRecording = false

    private var recordingStartDelay = 3000L // delay to start recording after button press (in ms)
    private var recordingAutostopDelay = 10000L // delay after which a recording is automatically stopped (in ms)
    private lateinit var startCountDown : CountDownTimer
    private var stopCountDown : CountDownTimer? = null
    private lateinit var countDownText : TextView
    private lateinit var vibrator : Vibrator

    private var markedTimeStamps: ArrayList<Long> = ArrayList()
    private var connectedSensors : Int = 0
    private var lostConnection : Boolean = false

    //Preferences
    private lateinit var sharedPrefs : SharedPreferences



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        sharedPrefs = getDefaultSharedPreferences(this)

        // Initialize textViews
        countDownText = findViewById(R.id.countDownText)

        // Initialize TextView for label selection
        labelTextView = this.findViewById(R.id.label)

        // Initialize vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        connectDialog = ScanDialogFragment().apply {
            initialize(
                { bluetoothAdapter!!.bluetoothLeScanner },
                { addDeviceFragment(it) },
                {
                    supportFragmentManager.fragments
                        .filterIsInstance<DeviceFragment>()
                        .none { knownDevice -> (knownDevice.device().address == it.address) }
                }
            )
        }

        // recording functionality
        vRecordButton.setOnClickListener {
            synchronized(vRecordButton) {
                if (!isRecording) { // currently not recording
                    if(supportFragmentManager.fragments.filterIsInstance<DeviceFragment>().isEmpty()){
                        Toast.makeText(this, "no recordable devices connected", Toast.LENGTH_LONG).show()
                    }else {
                        //get the delays from preferences
                        try {
                            recordingStartDelay = sharedPrefs!!.getString("SetPreRecTimer", "-1")
                                .toLong() * 1000L
                        } catch (ex: NumberFormatException) {
                            Toast.makeText(
                                this,
                                "Timer value must be a number & is set to 0!",
                                Toast.LENGTH_LONG
                            ).show()
                            sharedPrefs.edit().putString("SetPreRecTimer", "0").apply()
                        }
                        try {
                            recordingAutostopDelay =
                                sharedPrefs!!.getString("SetFixRecLen", "-1").toLong() * 1000L
                        } catch (ex: NumberFormatException) {
                            Toast.makeText(
                                this,
                                "Timer value must be a number & is set to 0!",
                                Toast.LENGTH_SHORT
                            ).show()
                            sharedPrefs.edit().putString("SetFixRecLen", "0").apply()
                        }


                        // start recording after delay if desired
                        if (sharedPrefs.getBoolean("PreRecTimer", false)) {
                            startCountDown = object : CountDownTimer(recordingStartDelay, 1000) {
                                override fun onTick(millisUntilFinished: Long) {
                                    countDownText.text = getString(R.string.startCountdownPrefix) +
                                            ((millisUntilFinished / 1000) + 1) +
                                            getString(R.string.secondPostfix)
                                }

                                override fun onFinish() {
                                    countDownText.text = ""
                                    startRecording()
                                }
                            }
                            startCountDown.start()
                        } else {
                            startRecording()
                        }
                        isRecording = true
                        vRecordButton.text = getString(R.string.stop)
                    }
                }else { // currently recording
                    if(supportFragmentManager.fragments.filterIsInstance<DeviceFragment>().size!=connectedSensors){
                        lostConnection = true
                    }
                    stopRecording()
                }
            }
        }

    }

    private fun startRecording() {

        connectedSensors = supportFragmentManager.fragments.filterIsInstance<DeviceFragment>().size


        if(sharedPrefs.getBoolean("FixRecLen", false)) {
            stopCountDown = object : CountDownTimer(recordingAutostopDelay, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    countDownText.text = getString(R.string.stopCountdownPrefix) +
                            ((millisUntilFinished / 1000) + 1) +
                            getString(R.string.secondPostfix)
                }

                override fun onFinish() {
                    if(supportFragmentManager.fragments.filterIsInstance<DeviceFragment>().size!=connectedSensors){
                        lostConnection = true
                    }
                    stopRecording()
                }
            }
            stopCountDown?.start()
        }
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))

        // start the recording
        val extremityDataArray = ArrayList<ExtremityData>()
        supportFragmentManager.fragments
            .filterIsInstance<DeviceFragment>()
            .forEach {
                val extremityData = ExtremityData()
                DeviceViewModel.forDeviceFragment(it).extremityData = extremityData
                extremityDataArray.add(extremityData)
            }

        if (sharedPrefs.getBoolean("labelRecording", false)){
            recLabel = labelTextView.text.toString()+"_"
        }else{
            recLabel=""
        }
        //val spinnerAdapter: AdapterWithCustomItem = activitySpinner.adapter as AdapterWithCustomItem
        recorder = GestureData(
            extremityDataArray.toTypedArray(),
            recLabel,
            this
        )
    }

    private fun stopRecording() {
        if (sharedPrefs.getBoolean("PreRecTimer", false)) {
            startCountDown.cancel()
        }
        if(sharedPrefs.getBoolean("FixRecLen", false)){
            stopCountDown?.cancel()
        }

        if(recorder != null) { // make sure there is a recording running
            recorder!!.endTime = SimpleDateFormat("yyyy-MM-dd--HH-mm-ss", Locale.US)
                .format(Date(System.currentTimeMillis()))

            // unassign all extremityData objects from the sensors.
            supportFragmentManager.fragments
                .filterIsInstance<DeviceFragment>()
                .forEach {
                    DeviceViewModel.forDeviceFragment(it).extremityData = null
                }

            recorder!!.markedTimeStamps = markedTimeStamps


            // show dialog to add textual note to recording if desired
            if(sharedPrefs.getBoolean("addNotes", false)){
                showNoteDialog()
            } else {
                if (lostConnection) {
                    safeIncompleteRec()
                }else {
                    endRecording()
                }
            }
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        }

        countDownText.text = ""
        vRecordButton.text = getString(R.string.start)
        isRecording = false
    }

    /**
     * add scan- and settings-button
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean =
        if (bleReady) {
            menuInflater.inflate(R.menu.main_menu, menu)
            true
        } else false

    /**
     * handle click on the scan- and settings-button
     */
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return if (item != null && item.itemId == R.id.search) {
            connectDialog.show(supportFragmentManager, null)
            true
        } else return if (item != null && item.itemId == R.id.menu_settings) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            true
        } else super.onOptionsItemSelected(item)
    }


    /**
     * remove the deviceFragment from the adapter-list
     */
    override fun removeDeviceFragment(device: BluetoothDevice) {
        supportFragmentManager.fragments
            .filterIsInstance<DeviceFragment>()
            .find { it.device().address == device.address }
            ?.let { supportFragmentManager.beginTransaction().remove(it).commit() }
    }

    /**
     * create a device-fragment. connect to the device.
     * add the fragment to the adapter-list so it gets attached
     */
    private fun addDeviceFragment(device: BluetoothDevice) {
        try {
            supportFragmentManager
                .beginTransaction()
                .add(vFragmentContainer.id, DeviceFragment.newInstance(device))
                .commit()
        } catch (e: Exception) {
            Log.w(TAG, "Could not add Device Fragment")
            e.printStackTrace()
        }
    }

    /**
     * when a deviceFragment is attached, initialize it
     */
    override fun onAttachFragment(fragment: Fragment) {
        super.onAttachFragment(fragment)
        if (fragment is DeviceFragment) fragment.setWriteToDeviceIfc(
            DeviceManager(
                fragment.device(),
                this,
                DeviceViewModel.forDeviceFragment(fragment),
                BluetoothGatt.CONNECTION_PRIORITY_BALANCED,
                ImuConfig(3, false) // odr = 208Hz
            )
        )
    }

    /**
     * check all the permissions and requirements for a ble scan.
     * if ready for a scan show the scan-button.
     * else resolve the error (show a dialog) and
     * call onResume again (automatically when the dialog is closed)
     */
    override fun onResume() {
        super.onResume()
        bleReady = false
        invalidateOptionsMenu()
        if (checkPermissions() && checkBluetoothEnabled() && checkLocationEnabled()) {
            bleReady = true
            invalidateOptionsMenu()
        }

        if (sharedPrefs.getBoolean("labelRecording", false)){
            findViewById<TextView>(R.id.labelText).visibility = View.VISIBLE
            findViewById<TextView>(R.id.label).visibility = View.VISIBLE
        }else{
            findViewById<TextView>(R.id.labelText).visibility = View.INVISIBLE
            findViewById<TextView>(R.id.label).visibility = View.INVISIBLE
        }
    }

    /**
     * check if bluetooth is available and enabled. ask for enabled if not
     * @return true if bluetooth was available and ready
     */
    private fun checkBluetoothEnabled(): Boolean {
        if (bluetoothAdapter != null) {
            return if (!bluetoothAdapter!!.isEnabled) {
                startActivityForResult(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    REQUEST_ENABLE_BT
                )
                false
            } else true
        } else {
            AlertDialog.Builder(this)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(R.string.bt_not_available)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
            return false
        }
    }

    /**
     * check for permissions. ask for permission if not
     * @return true if permission was granted
     */
    private fun checkPermissions(): Boolean =
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_PERMISSION
            )
            false
        } else true

    /**
     * check if location is enabled. ask for enabled if not
     * @return true if location was enabled
     */
    private fun checkLocationEnabled(): Boolean {
        val locationManager = (getSystemService(Context.LOCATION_SERVICE) as LocationManager?)
        return if (locationManager != null
            && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        ) true
        else {
            AlertDialog.Builder(this)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage("Please enable location on your device")
                .setPositiveButton(android.R.string.ok)
                { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .show()
            false
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSION = 2
        private val TAG = MainActivity::class.java.simpleName.filter { it.isUpperCase() }
    }

    private fun showNoteDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.addNoteTitle))

        // Set up the input
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        // Set up OK button
        builder.setPositiveButton(
            getString(R.string.ok)
        ) { dialog, which ->
            recorder?.note = input.text.toString()

            if (lostConnection) {
                safeIncompleteRec()
            }else {
                endRecording()
            }
        }

        builder.show()
    }

    private fun endRecording() {
        // write recorder object into file
        recorder?.let { FileOperations.writeGestureFile(it) }
        recorder = null
    }

    /**
     * Prevent connection loss by not terminating activity and stop recording instead.
     */
    override fun onBackPressed() {
        if (recorder != null)
            vRecordButton.performClick()
    }

    override fun onValueChange(p0: NumberPicker?, p1: Int, p2: Int) {
        // nothing to do
    }

    fun setLabel(v: View){
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.changeLabel))

        // Set up the input
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        // Set up OK button
        builder.setPositiveButton(
            getString(R.string.ok)
        ) { dialog, which ->
            labelTextView.text = input.text.toString()
        }

        builder.show()
    }

    fun markTimeStamp(v: View){
        if(isRecording){
            Toast.makeText(this, "marked timestamp!", Toast.LENGTH_SHORT).show()
            try {
                recorder!!.datas!![recorder!!.datas!!.lastIndex].accData.timeStamp.let {
                    markedTimeStamps.add(
                        it.last()
                    )
                }
            } catch (e: Exception){
                Toast.makeText(this, "no recordable devices connected", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun safeIncompleteRec(){

        lostConnection = false
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Warning!")
        builder.setMessage("It seems that one or more sensors where disconnected while recording the data. Do you want to safe the recording anyways?")


        // Set up Safe button
        builder.setPositiveButton(
            "Safe"
        ) { dialog, which ->
            endRecording()
        }
        // Set up Delete button
        builder.setNegativeButton(
            "Delete"
        ) { dialog, which ->
            //do nothing
        }

        builder.show()

    }

}