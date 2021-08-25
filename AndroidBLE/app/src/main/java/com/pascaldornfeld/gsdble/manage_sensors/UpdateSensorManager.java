package com.pascaldornfeld.gsdble.manage_sensors;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.pascaldornfeld.gsdble.R;
import com.pascaldornfeld.gsdble.database.MyDatabaseHelper;

public class UpdateSensorManager extends AppCompatActivity {

    TextView deviceMac_text;
    EditText deviceName_input, deviceDrift_input;
    Button update_btn;

    String deviceMac, deviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_sensor_manager);

        deviceMac_text = findViewById(R.id.deviceMac_textView);
        deviceName_input = findViewById(R.id.deviceName_input);
        update_btn = findViewById(R.id.update_button);

        getIntentData();

        update_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deviceName = deviceName_input.getText().toString().trim();
                MyDatabaseHelper myDB = new MyDatabaseHelper(UpdateSensorManager.this);
                myDB.updateDeviceName(deviceMac, deviceName);
            }
        });


        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle("Device Manager");
        }
    }

    void getIntentData(){
        if(getIntent().hasExtra("deviceMac") && getIntent().hasExtra("deviceName")){
            deviceMac = getIntent().getStringExtra("deviceMac");
            deviceName = getIntent().getStringExtra("deviceName");

            deviceMac_text.setText(deviceMac);
            deviceName_input.setText(deviceName);
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