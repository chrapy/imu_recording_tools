package com.pascaldornfeld.gsdble.manage_sensors.drift;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.pascaldornfeld.gsdble.R;
import com.pascaldornfeld.gsdble.database.MyDatabaseHelper;

public class UpdateSensorDriftManager extends AppCompatActivity {

    TextView deviceMac_text, deviceName_text;
    EditText deviceDrift_input;
    Button update_btn;

    String deviceMac, deviceDrift, deviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.update_drift_sensor_manager);

        deviceMac_text = findViewById(R.id.deviceMac_textView);
        deviceName_text = findViewById(R.id.deviceName_textView);
        deviceDrift_input = findViewById(R.id.deviceDrift_input);
        update_btn = findViewById(R.id.update_button);

        getIntentData();

        update_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deviceDrift = deviceDrift_input.getText().toString().trim();
                MyDatabaseHelper myDB = new MyDatabaseHelper(UpdateSensorDriftManager.this);
                myDB.updateDeviceTimeDrift(deviceMac, deviceDrift);
                myDB.close();
            }
        });



        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle("Device Manager");
        }
    }

    void getIntentData(){
        if(getIntent().hasExtra("deviceMac") && getIntent().hasExtra("deviceDrift")){
            deviceMac = getIntent().getStringExtra("deviceMac");
            deviceName = getIntent().getStringExtra("deviceName");
            deviceDrift = getIntent().getStringExtra("deviceDrift");

            deviceMac_text.setText(deviceMac);
            deviceName_text.setText(deviceName);
            deviceDrift_input.setText(deviceDrift);
        }else{
            Toast.makeText(this, "No data!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@Nullable MenuItem item) {

        if (item != null) {
            onBackPressed();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
}