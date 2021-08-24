package com.pascaldornfeld.gsdble.manage_sensors;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pascaldornfeld.gsdble.R;

import java.util.ArrayList;

public class SensorManagerAdapter extends RecyclerView.Adapter<SensorManagerAdapter.MyViewHolder> {

    private Context context;
    private ArrayList  deviceMac, deviceName;


    SensorManagerAdapter(Context context, ArrayList deviceMac, ArrayList deviceName){
        this.context = context;
        this.deviceMac = deviceMac;
        this.deviceName = deviceName;
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
    }

    @Override
    public int getItemCount() {
        return deviceMac.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        TextView deviceMac_text, deviceName_text;
        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceMac_text = itemView.findViewById(R.id.deviceMac_text);
            deviceName_text = itemView.findViewById(R.id.deviceName_text);
        }
    }


}
