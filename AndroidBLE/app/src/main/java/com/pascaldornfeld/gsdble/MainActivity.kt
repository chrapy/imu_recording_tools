package com.pascaldornfeld.gsdble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.*
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
import com.pascaldornfeld.gsdble.audio.AudioPlayer
import com.pascaldornfeld.gsdble.connected.DeviceViewModel
import com.pascaldornfeld.gsdble.connected.hardware_library.DeviceManager
import com.pascaldornfeld.gsdble.connected.hardware_library.models.ImuConfig
import com.pascaldornfeld.gsdble.connected.view.DeviceFragment
import com.pascaldornfeld.gsdble.database.MyDatabaseHelper
import com.pascaldornfeld.gsdble.file_dumping.ExtremityData
import com.pascaldornfeld.gsdble.file_dumping.FileOperations
import com.pascaldornfeld.gsdble.file_dumping.GestureData
import com.pascaldornfeld.gsdble.scan.ScanDialogFragment
import kotlinx.android.synthetic.main.main_activity.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity(), DeviceFragment.RemovableDeviceActivity,
    NumberPicker.OnValueChangeListener {
    private var bleReady = true
    private val bluetoothAdapter: BluetoothAdapter? by lazy { (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter }
    private lateinit var connectDialog: ScanDialogFragment

    private var recorder: GestureData? = null
    private lateinit var recLabel:String
    private lateinit var labelTextView : TextView
    private var isRecording = false
    private var renewRecording = false
    private var abortRenewRec = false
    private var deletedLastRec = false

    private var recordingStartDelay = 3000L // delay to start recording after button press (in ms)
    private var recordingAutostopDelay = 10000L // delay after which a recording is automatically stopped (in ms)
    private var autoStartDelay = 0L // delay between automated recordings (in ms)
    private lateinit var startCountDown : CountDownTimer
    private var stopCountDown : CountDownTimer? = null
    private lateinit var countDownText : TextView
    private lateinit var vibrator : Vibrator

    private var markedTimeStamps: ArrayList<Long> = ArrayList()
    private var connectedSensors : Int = 0
    private var lostConnection : Boolean = false

    private lateinit var audio : AudioPlayer
    private var stopRecording : Boolean = false

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
                { checkSensorKnown(it) },
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
                if (renewRecording) {
                    if (sharedPrefs.getBoolean("PreRecTimer", false)) {
                        startCountDown.cancel()
                    }
                    if(sharedPrefs.getBoolean("FixRecLen", false)){
                        stopCountDown?.cancel()
                    }
                    countDownText.text = ""
                    vRecordButton.text = getString(R.string.start)
                    isRecording = false
                    abortRenewRec = true
                    audio.speak("Stopped Recording Streak")
                } else {
                    if (!isRecording) { // currently not recording
                        if (supportFragmentManager.fragments.filterIsInstance<DeviceFragment>()
                                .isEmpty()
                        ) {
                            Toast.makeText(
                                this,
                                "no recordable devices connected",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            //get the delays from preferences
                            try {
                                recordingStartDelay =
                                    sharedPrefs!!.getString("SetPreRecTimer", "-1")
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
                                startCountDown =
                                    object : CountDownTimer(recordingStartDelay, 1000) {
                                        override fun onTick(millisUntilFinished: Long) {
                                            countDownText.text =
                                                getString(R.string.startCountdownPrefix) +
                                                        ((millisUntilFinished / 1000) + 1) +
                                                        getString(R.string.secondPostfix)
                                            if (sharedPrefs.getBoolean("audioOutput", false)) {
                                                audio.speak(((millisUntilFinished / 1000) + 1).toString())
                                            }
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
                            stopRecording = false
                        }
                    } else { // currently recording
                        if (supportFragmentManager.fragments.filterIsInstance<DeviceFragment>().size != connectedSensors) {
                            lostConnection = true
                        }
                        stopRecording = true
                        stopRecording()
                    }
                }
            }
        }
        audio = AudioPlayer(this)
    }

    private fun startRecording() {

        connectedSensors = supportFragmentManager.fragments.filterIsInstance<DeviceFragment>().size
        deletedLastRec = false

        if(sharedPrefs.getBoolean("FixRecLen", false)) {
            stopCountDown = object : CountDownTimer(recordingAutostopDelay, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    countDownText.text = getString(R.string.stopCountdownPrefix) +
                            ((millisUntilFinished / 1000) + 1) +
                            getString(R.string.secondPostfix)
                    if (sharedPrefs.getBoolean("audioOutput", false)) {
                        if (((millisUntilFinished / 1000) + 1)<=3){
                            audio.speak(((millisUntilFinished / 1000) + 1).toString())
                        }
                    }
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
        if (sharedPrefs.getBoolean("audioOutput", false)) {
            audio.speak("Recording started")
        }
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))


        // start the recording
        vRecordButton.text = getString(R.string.stop)
        isRecording = true
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
            if (sharedPrefs.getBoolean("audioOutput", false)) {
                audio.speak("Recording stopped")
            }

            recorder!!.endTime = SimpleDateFormat("yyyy-MM-dd--HH-mm-ss", Locale.US)
                .format(Date(System.currentTimeMillis()))

            // unassign all extremityData objects from the sensors.
            supportFragmentManager.fragments
                .filterIsInstance<DeviceFragment>()
                .forEach {
                    DeviceViewModel.forDeviceFragment(it).extremityData = null
                }

            recorder!!.markedTimeStamps = markedTimeStamps

            countDownText.text = ""
            vRecordButton.text = getString(R.string.start)
            isRecording = false

            // show dialog to add textual note to recording if desired
            if(sharedPrefs.getBoolean("addNotes", false)){
                if(!sharedPrefs.getBoolean("startNextAuto", false)){
                    showNoteDialog()
                } else {
                    Toast.makeText(this, "you can't add notes to automated recording streaks", Toast.LENGTH_SHORT).show()
                    if (lostConnection) {
                        safeIncompleteRec()
                    }else {
                        endRecording()
                    }
                }
            } else {
                if (lostConnection) {
                    safeIncompleteRec()
                }else {
                    endRecording()
                }
            }
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        }


    }

    /**
     * add scan-, settings- and delete-button
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean =
        if (bleReady) {
            menuInflater.inflate(R.menu.main_menu, menu)
            true
        } else false

    /**
     * handle click on the scan-, settings- and delete-button
     */
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return if (item != null && item.itemId == R.id.search) {
            connectDialog.show(supportFragmentManager, null)
            true
        } else return if (item != null && item.itemId == R.id.menu_settings) {
            if (!isRecording) {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivityForResult(intent, 2)
            }
            true
        }else return if (item != null && item.itemId == R.id.deleteLastRec) {
            if (!isRecording) {
                deleteLastRecording()
            }
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
        if(sharedPrefs.getBoolean("startNextAuto", false) && !stopRecording){
            renewRecording()
        }
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

    fun markTimeStamp(v:View){
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

    /**
     * If sensors disconnect during the recording, the user is warned afterwards and asked if he wants to keep or delete the recording
     */
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

    /**
     * With the renewRecording Options selected (in the settings menu) the recording will be renewed until stopped by the user
     */
    private fun renewRecording(){
        vRecordButton.text = getString(R.string.stop)
        renewRecording = true
        abortRenewRec = false

        var textDone = false
        //get the delays from the preferences
        try {
            autoStartDelay = sharedPrefs!!.getString("startAutoPause", "-1")
                .toLong() * 1000L
        } catch (ex: NumberFormatException) {
            Toast.makeText(
                this,
                "Timer value must be a number & is set to 0!",
                Toast.LENGTH_LONG
            ).show()
            sharedPrefs.edit().putString("startAutoPause", "0").apply()
            autoStartDelay = 0
        }

        // start recording after delay if desired

        startCountDown = object : CountDownTimer(autoStartDelay, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if(!abortRenewRec) {
                    countDownText.text = getString(R.string.startCountdownPrefix) +
                            ((millisUntilFinished / 1000) + 1) +
                            getString(R.string.secondPostfix)
                    if (sharedPrefs.getBoolean("audioOutput", false)) {
                        if (!textDone) {
                            audio.speak("New recording in")
                            textDone = true
                        } else {
                            audio.speak(((millisUntilFinished / 1000) + 1).toString())
                        }
                    }
                }
            }

            override fun onFinish() {
                countDownText.text = ""
                renewRecording = false
                if(!abortRenewRec) {
                    startRecording()
                }
            }
        }
        startCountDown.start()

    }

    /**
     * Option to delete the last recording in App
     */

    fun deleteLastRecording(){
        if(!deletedLastRec) {
            if (FileOperations.lastFile != null) {
                FileOperations.lastFile!!.delete()
                Toast.makeText(
                    this,
                    "Deleted last recording",
                    Toast.LENGTH_SHORT
                ).show()
            }else{
                Toast.makeText(
                    this,
                    "This folder doesn't contain a file",
                    Toast.LENGTH_SHORT
                ).show()
            }
            deletedLastRec = true
        } else {
            Toast.makeText(
                this,
                "The last recording was already deleted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Checks if the Sensor Device is already known, if not it will be added.
     * The time drift of the sensor will be determined and a name/could be attached to the specific sensor
     */
    private fun checkSensorKnown(device: BluetoothDevice){

        var myDB = MyDatabaseHelper(this)
        var deviceMac = device.address
        var deviceName = deviceMac


        if (!myDB.checkDevice(deviceMac)){

            //Possibility to attach a label to the Sensor

            val builder = AlertDialog.Builder(this)
            builder.setTitle("New Sensor")
            builder.setMessage("Do you want to label the Sensor")
            // Set up Safe button
            builder.setPositiveButton(
                "Label"
            ) { _, _ ->
                nameDevice(myDB, device)
            }
            // Set up Skip button
            builder.setNegativeButton(
                "Skip"
            ) { _, _ ->
                myDB.addDevice(deviceMac, deviceName, "ermitteln")
                addDeviceFragment(device)
            }
            builder.show()

            //TODO Popups zum ermitteln von drift (evtl extra Methoden)





        } else {
            addDeviceFragment(device)
        }

    }

    private fun nameDevice(myDB: MyDatabaseHelper, device: BluetoothDevice){
        var deviceName = ""
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Name the device")

        // Set up the input
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        // Set up OK button
        builder.setPositiveButton(
            getString(R.string.ok)
        ) { dialog, which ->
            deviceName = input.text.toString()
            myDB.addDevice(device.address, deviceName, "ermitteln")
            addDeviceFragment(device)
        }

        builder.show()
    }

    /**
     * refresh device names, when returning from the settings screen
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode==2){

            for (fragment in supportFragmentManager.fragments){
                if (fragment is DeviceFragment) fragment.setDeviceName()
            }
        }
    }

}