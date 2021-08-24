package com.pascaldornfeld.gsdble.manage_sensors

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pascaldornfeld.gsdble.R
import com.pascaldornfeld.gsdble.database.MyDatabaseHelper

class SensorManager : AppCompatActivity() {

    lateinit var myDB:MyDatabaseHelper
    lateinit var deviceMac:ArrayList<String>
    lateinit var deviceName:ArrayList<String>
    lateinit var deviceDrift:ArrayList<String>

    lateinit var smAdapter:SensorManagerAdapter

    private lateinit var recyclerView:RecyclerView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sensormanager_activity)

        recyclerView = findViewById(R.id.sensorManagerRecyclerView)


        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        myDB = MyDatabaseHelper(this)
        deviceMac = arrayListOf()
        deviceName = arrayListOf()
        deviceDrift = arrayListOf()

        storeData()

        smAdapter = SensorManagerAdapter(this, deviceMac, deviceName)
        recyclerView.adapter = smAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return if (item != null) {
            onBackPressed()
            true
        } else super.onOptionsItemSelected(item)
    }

    /**
     * store the data from the database in ArrayLists
     */
    private fun storeData(){
        var cursor = myDB.readAllData()
        if(cursor.count ==0){
            Toast.makeText(this, "No data.", Toast.LENGTH_SHORT).show()
        }else{
            while (cursor.moveToNext()){
                deviceMac.add(cursor.getString(0))
                deviceName.add(cursor.getString(1))
                deviceDrift.add(cursor.getString(2))
            }
        }
    }
    
}