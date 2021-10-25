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
import android.provider.SyncStateContract.Helpers.set
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
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
import com.pascaldornfeld.gsdble.file_dumping.SensorData
import com.pascaldornfeld.gsdble.preprocessing.PreprocessingRunnable
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

    private var recordingStartSystemTime = 0L
    private var markedTimeStamps: ArrayList<Long> = ArrayList()
    private var connectedSensors : Int = 0
    private var lostConnection : Boolean = false
    private var tooFewPakets:Boolean = false

    private lateinit var audio : AudioPlayer
    private var stopRecording : Boolean = false

    private var calculatedDrifts = ArrayList<Double>()


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

    //usable as api
    fun startRecording() {

        connectedSensors = supportFragmentManager.fragments.filterIsInstance<DeviceFragment>().size
        deletedLastRec = false

        if(sharedPrefs.getBoolean("FixRecLen", false)) {
            stopCountDown = object : CountDownTimer(recordingAutostopDelay, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    if (::countDownText.isInitialized) {
                        countDownText.text = getString(R.string.stopCountdownPrefix) +
                                ((millisUntilFinished / 1000) + 1) +
                                getString(R.string.secondPostfix)
                        if (sharedPrefs.getBoolean("audioOutput", false)) {
                            if (((millisUntilFinished / 1000) + 1) <= 3) {
                                if(::audio.isInitialized) {
                                    audio.speak(((millisUntilFinished / 1000) + 1).toString())
                                }
                            }
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
            if(::audio.isInitialized){
                audio.speak("Recording started")
            }
        }

        if(::vibrator.isInitialized){
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        }

        // start the recording

        //if used as api there is a possibility vRecordButton does not exist (catch this case)
        try {
            vRecordButton.text = getString(R.string.stop)
        } catch (e:Exception){
            //do nothing
        }

        isRecording = true
        recordingStartSystemTime = System.currentTimeMillis()
        val extremityDataArray = ArrayList<ExtremityData>()
        supportFragmentManager.fragments
            .filterIsInstance<DeviceFragment>()
            .forEach {
                val extremityData = ExtremityData()
                DeviceViewModel.forDeviceFragment(it).extremityData = extremityData
                extremityDataArray.add(extremityData)
            }

        if (sharedPrefs.getBoolean("tooFewData", false)){
            tooFewPakets = false
            supportFragmentManager.fragments
                .filterIsInstance<DeviceFragment>()
                .forEach {
                    it.initaliseDataRateTracking(it.getODR())
                }
        }
        if (sharedPrefs.getBoolean("labelRecording", false)){
            recLabel = labelTextView.text.toString()
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

    //usable as api
    fun stopRecording() {
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

            //update device drift and name in case it changed since connecting the device
            var myDB = MyDatabaseHelper(this)
            recorder!!.datas.forEach {
                it.deviceDrift =  myDB.getDeviceDrift(it.deviceMac)
                it.deviceName = myDB.getDeviceName(it.deviceMac)
            }
            myDB.close()

            // unassign all extremityData objects from the sensors.
            supportFragmentManager.fragments
                .filterIsInstance<DeviceFragment>()
                .forEach {
                    DeviceViewModel.forDeviceFragment(it).extremityData = null
                }

            recorder!!.markedTimeStamps = markedTimeStamps

            markedTimeStamps = ArrayList<Long>()


            //if used as api there is a possibility vRecordButton does not exist (catch this case)
            try {
                countDownText.text = ""
                vRecordButton.text = getString(R.string.start)
                isRecording = false
            } catch (e:Exception) {
                //do nothing
            }


            // show dialog to add textual note to recording if desired
            if(sharedPrefs.getBoolean("addNotes", false)){
                if(!sharedPrefs.getBoolean("startNextAuto", false)){
                    showNoteDialog()
                } else {
                    Toast.makeText(
                        this,
                        "you can't add notes to automated recording streaks",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (lostConnection) {
                        safeIncompleteRec()
                    }else {
                        endRecording()
                    }
                }
            } else {

                if (sharedPrefs.getBoolean("tooFewData", false)){
                    var minX = 0L

                    //get the percentage from preferences
                    try {
                        minX =
                            sharedPrefs.getString("setMinDataRate", "0")
                                .toLong()
                    } catch (ex: NumberFormatException) {
                        Toast.makeText(
                            this,
                            "Value must be a number & is set to 0!",
                            Toast.LENGTH_LONG
                        ).show()
                        sharedPrefs.edit().putString("setMinDataRate", "0").apply()
                        minX = 0
                    }

                    var minXPercent = minX/100.0



                    supportFragmentManager.fragments
                        .filterIsInstance<DeviceFragment>()
                        .forEach {
                            it.getLowestDataRate()
                            if (it.getLowestDataRate()<it.getODR()*minXPercent){
                                tooFewPakets = true
                            }
                        }
                }

                if (lostConnection || tooFewPakets) {
                    if (lostConnection) {
                        safeIncompleteRec()
                    }
                    if (tooFewPakets){
                        safeWithPaketLoss()
                    }
                }else {
                    endRecording()
                }
            }

            if (::vibrator.isInitialized){
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            }
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
                .commitNow()
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
        // if not deactivated write recorder object into file
        if(!sharedPrefs.getBoolean("dontSafeRawData", false)){
            recorder?.let { FileOperations.writeGestureFile(it) }
        }
        if(sharedPrefs.getBoolean("enablePreprocessing", false)) {

            val r: Runnable = PreprocessingRunnable(recorder, sharedPrefs, this)
            Thread(r).start()
        }
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

    /**
     * safe timestamps of the current recording (based on system time) whenever the "mark timestamp" button is triggered
     */
    fun markTimeStamp(v: View){
        if(isRecording){
            Toast.makeText(this, "marked timestamp!", Toast.LENGTH_SHORT).show()
            try {
                markedTimeStamps.add(
                    (System.currentTimeMillis() - recordingStartSystemTime)
                )

                /*
                recorder!!.datas!![recorder!!.datas!!.lastIndex].accData.timeStamp.let {
                    markedTimeStamps.add(
                        it.last()
                    )
                }

                 */
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
     * If sensors disconnect during the recording, the user is warned afterwards and asked if he wants to keep or delete the recording
     */
    private fun safeWithPaketLoss(){

        tooFewPakets = false
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Warning!")
        builder.setMessage("The data rate was less than desired. Do you want to safe the recording anyways?")


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
        autoStartDelay = try {
            sharedPrefs!!.getString("startAutoPause", "-1")
                .toLong() * 1000L
        } catch (ex: NumberFormatException) {
            Toast.makeText(
                this,
                "Timer value must be a number & is set to 0!",
                Toast.LENGTH_LONG
            ).show()
            sharedPrefs.edit().putString("startAutoPause", "0").apply()
            0
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

    private fun deleteLastRecording(){
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
                myDB.addDevice(deviceMac, deviceName, "unknown")

                addDeviceFragment(device)

                myDB.close()
                setUpDriftCalculation(device)

            }
            builder.show()
        } else {
            addDeviceFragment(device)
        }

        myDB.close()

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
            myDB.addDevice(device.address, deviceName, "unknown")

            addDeviceFragment(device)
            myDB.close()
            setUpDriftCalculation(device)
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

    private fun setUpDriftCalculation(device: BluetoothDevice) {

        //set up a loading screen, while calculating the device drift
        var calculateDriftProgress: ProgressBar = findViewById(R.id.calculateDrift_progress)
        calculateDriftProgress.visibility = View.VISIBLE

        var calculatingTextView: TextView = findViewById(R.id.calculatingText)
        calculatingTextView.visibility = View.VISIBLE

        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        );

        var deviceMac = device.address

        var recordingTimesMillies = ArrayList<Long>()
        recordingTimesMillies.add(2000)
        recordingTimesMillies.add(5000)
        recordingTimesMillies.add(10000)
        recordingTimesMillies.add(30000)
        recordingTimesMillies.add(60000)

        //wait 5 seconds for connection to finish
        var countdown=
            object : CountDownTimer(5000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                }

                override fun onFinish() {
                    //check if new device is still connected and run the drift calculation
                    var stillConnected = false
                    for (deviceFragment in supportFragmentManager.fragments.filterIsInstance<DeviceFragment>()){
                        if(deviceFragment.device().address == deviceMac){
                            stillConnected = true
                        }
                    }

                    if (!stillConnected){
                        var myDB = MyDatabaseHelper(this@MainActivity)
                        myDB.deleteDevice(deviceMac)
                        Toast.makeText(
                            this@MainActivity,
                            "The Sensor was disconnected, please try again!",
                            Toast.LENGTH_LONG
                        ).show()
                        myDB.close()
                        stopCalculatingView()
                    }else{
                        recordForDriftCalculation(recordingTimesMillies, deviceMac)
                    }
                }
            }
        countdown?.start()

    }




    private fun calculateDrift(
        recordingTimes: ArrayList<Long>,
        timestamps: ArrayList<Long>,
        deviceMac: String
    ){

        var recordingTime = recordingTimes[0].toDouble()

        if(timestamps.size == 0){
            var myDB = MyDatabaseHelper(this)
            myDB.deleteDevice(deviceMac)
            Toast.makeText(
                this@MainActivity,
                "The Sensor was disconnected, please try again!",
                Toast.LENGTH_LONG
            ).show()
            myDB.close()
            stopCalculatingView()
        }else {
            //driftfactor = total sensor-recording-time / recording time measured by the app
            var sensorRecTime = (timestamps.last() - timestamps.first()).toDouble()
            var drift = sensorRecTime / recordingTime
            calculatedDrifts.add(drift)

            recordingTimes.removeAt(0)

            if (recordingTimes.size <= 0) {
                //check if new device is still connected and finish the drift calculation
                var stillConnected = false
                for (deviceFragment in supportFragmentManager.fragments.filterIsInstance<DeviceFragment>()) {
                    if (deviceFragment.device().address == deviceMac) {
                        stillConnected = true
                    }
                }

                if (!stillConnected) {
                    var myDB = MyDatabaseHelper(this)
                    myDB.deleteDevice(deviceMac)
                    Toast.makeText(
                        this@MainActivity,
                        "The Sensor was disconnected, please try again!",
                        Toast.LENGTH_LONG
                    ).show()
                    myDB.close()
                    stopCalculatingView()
                } else {
                    finishDriftCalc(deviceMac)
                }

            } else {
                //check if new device is still connected and continue the drift calculation
                var stillConnected = false
                for (deviceFragment in supportFragmentManager.fragments.filterIsInstance<DeviceFragment>()) {
                    if (deviceFragment.device().address == deviceMac) {
                        stillConnected = true
                    }
                }

                if (!stillConnected) {
                    var myDB = MyDatabaseHelper(this)
                    myDB.deleteDevice(deviceMac)
                    Toast.makeText(
                        this@MainActivity,
                        "The Sensor was disconnected, please try again!",
                        Toast.LENGTH_LONG
                    ).show()
                    myDB.close()
                    stopCalculatingView()
                } else {
                    recordForDriftCalculation(recordingTimes, deviceMac)
                }
            }
        }
    }

    private fun recordForDriftCalculation(recordingTimes: ArrayList<Long>, deviceMac: String){

        var neededDevice: DeviceFragment = supportFragmentManager.fragments.filterIsInstance<DeviceFragment>().first()
        var driftRecorder: GestureData? = null
        var recordingTime = recordingTimes[0]
        var recTimeStamps = ArrayList<Long>()

        for (deviceFragment in supportFragmentManager.fragments.filterIsInstance<DeviceFragment>()){
            if(deviceFragment.device().address == deviceMac){
                neededDevice = deviceFragment
            }
        }


        var recCountdown=
        object : CountDownTimer(recordingTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                driftRecorder!!.endTime = SimpleDateFormat("yyyy-MM-dd--HH-mm-ss", Locale.US).format(
                    Date(
                        System.currentTimeMillis()
                    )
                )

                // unassign all extremityData objects from the sensor
                supportFragmentManager.fragments
                      .filterIsInstance<DeviceFragment>()
                      .forEach {
                          DeviceViewModel.forDeviceFragment(it).extremityData = null
                      }

                driftRecorder!!.markedTimeStamps = ArrayList()
                isRecording = false

                driftRecorder!!.datas.forEach { recTimeStamps = it.accData.timeStamp }

                calculateDrift(recordingTimes, recTimeStamps, deviceMac)
            }
        }
        recCountdown?.start()


        // start the recording
        isRecording = true
        val extremityDataArray = ArrayList<ExtremityData>()
        val extremityData = ExtremityData()
        DeviceViewModel.forDeviceFragment(neededDevice).extremityData = extremityData
        extremityDataArray.add(extremityData)

        recLabel = "CalculateDrift_" + recordingTime + "Seconds"

        driftRecorder = GestureData(
            extremityDataArray.toTypedArray(),
            recLabel,
            this
        )
    }

    private fun finishDriftCalc(deviceMac: String){
        var finalDrift = 0.0

        for (d in calculatedDrifts){
            finalDrift += d
        }

        finalDrift /= calculatedDrifts.size

        calculatedDrifts=ArrayList<Double>()

        var myDB = MyDatabaseHelper(this)
        myDB.updateDeviceTimeDrift(deviceMac, finalDrift.toString())
        myDB.close()

        Toast.makeText(this, "Sucessfully calculated drift!", Toast.LENGTH_LONG).show()

        stopCalculatingView()
    }

    private fun stopCalculatingView(){
        var calculateDriftProgress: ProgressBar = findViewById(R.id.calculateDrift_progress)
        var calculatingTextView: TextView = findViewById(R.id.calculatingText)
        calculateDriftProgress.visibility = View.INVISIBLE
        calculatingTextView.visibility = View.INVISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    //connecting sensors is possible via api
    fun connectionDialog(){
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
        connectDialog.show(supportFragmentManager, null)
    }
}