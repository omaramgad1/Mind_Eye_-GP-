package com.grad.gp.Bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.grad.gp.R;

import java.util.ArrayList;

public class DevicesAdapter extends RecyclerView.Adapter<DevicesAdapter.myDevicesViewHolder> {
    private Context context;
    private ArrayList<BluetoothDevice> devicesList;
    OnDeviceClickListener mListener;

    public DevicesAdapter(Context context, ArrayList<BluetoothDevice> devicesList) {
        this.context = context;
        this.devicesList = devicesList;

    }


    @NonNull
    @Override
    public myDevicesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.device_list_item, parent, false);

        DevicesAdapter.myDevicesViewHolder viewHolder = new DevicesAdapter.myDevicesViewHolder(view, mListener);


        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull DevicesAdapter.myDevicesViewHolder holder, int position) {
        BluetoothDevice device = devicesList.get(position);

        holder.text1.setText(device.getName());
        holder.text2.setText(device.getAddress());
    }

    @Override
    public int getItemCount() {
        return devicesList.size();
    }

    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        mListener = listener;
    }

    public interface OnDeviceClickListener {
        void OnDeviceClicked(String deviceAdd);
    }


    public class myDevicesViewHolder extends RecyclerView.ViewHolder {

        TextView text1;
        TextView text2;

        public myDevicesViewHolder(@NonNull View itemView, final OnDeviceClickListener listener) {
            super(itemView);

            text1 = itemView.findViewById(R.id.text1);
            text2 = itemView.findViewById(R.id.text2);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            listener.OnDeviceClicked(devicesList.get(position).getAddress());
                        }
                    }
                }
            });
        }
    }
}
