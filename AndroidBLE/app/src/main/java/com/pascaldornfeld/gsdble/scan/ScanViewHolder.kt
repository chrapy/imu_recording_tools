package com.pascaldornfeld.gsdble.scan


import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pascaldornfeld.gsdble.R
import kotlinx.android.synthetic.main.connect_viewholder.view.*

/**
 * viewHolder to show a scan result
 *
 * @param connectCallback what to do, when user wants to doConnect to the device
 * @param parentViewGroup viewGroup where viewHolder should be added
 */
class ScanViewHolder(val connectCallback: (BluetoothDevice) -> Unit, parentViewGroup: ViewGroup) :
    RecyclerView.ViewHolder(
        LayoutInflater.from(parentViewGroup.context).inflate(
            R.layout.connect_viewholder,
            parentViewGroup,
            false
        )
    ) {
    /**
     * bind view to a scan result
     */
    fun bind(scanResult: ScanResult) {
        itemView.vDeviceName.text = scanResult.device.name
        itemView.vDeviceAddress.text = scanResult.device.address
        itemView.vConnectButton.setOnClickListener { connectCallback(scanResult.device) }
    }
}
