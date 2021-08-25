package com.pascaldornfeld.gsdble.manage_sensors;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pascaldornfeld.gsdble.R;

import java.util.ArrayList;

public class SensorManagerAdapter extends RecyclerView.Adapter<SensorManagerAdapter.MyViewHolder> {

    private Context context;
    Activity activity;
    private ArrayList  deviceMac, deviceName,deviceDrift;


    SensorManagerAdapter(Activity activity, Context context, ArrayList deviceMac, ArrayList deviceName, ArrayList deviceDrift){
        this.activity = activity;
        this.context = context;
        this.deviceMac = deviceMac;
        this.deviceName = deviceName;
        this.deviceDrift = deviceDrift;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.known_sensor, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        holder.deviceMac_text.setText(String.valueOf(deviceMac.get(position)));
        holder.deviceName_text.setText(String.valueOf(deviceName.get(position)));
        holder.knownSensorLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, UpdateSensorManager.class);
                intent.putExtra("deviceMac", String.valueOf(deviceMac.get(position)));
                intent.putExtra("deviceName", String.valueOf(deviceName.get(position)));
                //intent.putExtra("deviceDrift", String.valueOf(deviceDrift.get(position)));
                activity.startActivityForResult(intent, 1);
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceMac.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        TextView deviceMac_text, deviceName_text;
        LinearLayout knownSensorLayout;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceMac_text = itemView.findViewById(R.id.deviceMac_textView);
            deviceName_text = itemView.findViewById(R.id.deviceName_text);
            knownSensorLayout = itemView.findViewById(R.id.knownSensorLayout);
        }
    }


}
