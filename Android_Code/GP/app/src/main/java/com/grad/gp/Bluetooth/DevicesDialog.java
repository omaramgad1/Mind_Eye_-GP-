package com.grad.gp.Bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.grad.gp.Common.AppConstants;
import com.grad.gp.Home.VisuallyImpaired;
import com.grad.gp.R;

import java.util.ArrayList;
import java.util.Collections;

public class DevicesDialog extends DialogFragment implements ServiceConnection, SerialListener, DevicesAdapter.OnDeviceClickListener {

    private BluetoothAdapter bluetoothAdapter;
    View v;
    private final ArrayList<BluetoothDevice> listItems = new ArrayList<>();
    private DevicesAdapter listAdapter;
    String TAG = "Bluetooth";


    private boolean initialStart = true;
    private String newline = TextUtil.newline_crlf;
    int stringSize = 0;
    String img64 = "";
    String deviceAddress = "";

    private RecyclerView mDevicesRecyclerView;
    private LinearLayoutManager mLayoutManager;

    ImageView mSettingsBtn;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        listAdapter = new DevicesAdapter(getContext(), listItems);
        listAdapter.setOnDeviceClickListener(this);

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.bluetooth_dialog, container, false);

        mDevicesRecyclerView = v.findViewById(R.id.list_devices_recycler);
        mLayoutManager = new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false);
        mDevicesRecyclerView.setLayoutManager(mLayoutManager);
        if (listAdapter != null) {
            mDevicesRecyclerView.setAdapter(listAdapter);
        }

        mSettingsBtn = v.findViewById(R.id.bluetooth_setting_btn);
        mSettingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(intent);
            }
        });
        return v;

    }

    @Override
    public void onResume() {
        super.onResume();
        if (bluetoothAdapter == null)
            Toast.makeText(getContext(), "bluetooth not supported", Toast.LENGTH_SHORT).show();
        else if (!bluetoothAdapter.isEnabled())
            Toast.makeText(getContext(), "bluetooth is disabled", Toast.LENGTH_SHORT).show();
        refresh();

    }


    void refresh() {
        listItems.clear();
        if (bluetoothAdapter != null) {
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices())
                if (device.getType() != BluetoothDevice.DEVICE_TYPE_LE)
                    listItems.add(device);
        }
        Collections.sort(listItems, DevicesDialog::compareTo);
        listAdapter.notifyDataSetChanged();
    }


    /**
     * sort by name, then address. sort named devices first
     */
    static int compareTo(BluetoothDevice a, BluetoothDevice b) {
        boolean aValid = a.getName() != null && !a.getName().isEmpty();
        boolean bValid = b.getName() != null && !b.getName().isEmpty();
        if (aValid && bValid) {
            int ret = a.getName().compareTo(b.getName());
            if (ret != 0) return ret;
            return a.getAddress().compareTo(b.getAddress());
        }
        if (aValid) return -1;
        if (bValid) return +1;
        return a.getAddress().compareTo(b.getAddress());
    }

    ////////////////////////////////


    @Override
    public void onDestroy() {
//        if (AppConstants.connected != AppConstants.Connected.False)
//            disconnect();
//        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        AppConstants.service = ((SerialService.SerialBinder) binder).getService();
        AppConstants.service.attach(this);
        if (initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        AppConstants.service = null;
    }


    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            Log.e(TAG, "connect: " + "connecting...");
            AppConstants.connected = AppConstants.Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            AppConstants.service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    public static void disconnect() {
        AppConstants.connected = AppConstants.Connected.False;
        AppConstants.service.disconnect();
    }


    private void receive(byte[] data) {

        String msg = new String(data);
        if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
            msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
        }
        if (msg != null && msg.length() < 9) {
            Log.e("TAG", "receive: " + msg);
            stringSize = Integer.parseInt(msg);
            Log.e("TAG", "receive: " + stringSize);
        } else {
            img64 += msg;
        }
        if (img64.length() == stringSize) {
            byte[] decodedString = Base64.decode(img64, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
//            receivedImage.setImageBitmap(decodedByte);
            AppConstants.bluetoothImageBitMap = decodedByte;
            img64 = "";
            Log.e(TAG, "receive Image: " + "DONE");
        }

    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        Log.e(TAG, "onSerialConnect: " + "connected");
        Toast.makeText(getContext(), "Connected Successfully", Toast.LENGTH_SHORT).show();
        AppConstants.connected = AppConstants.Connected.True;
        dismiss();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        Log.e(TAG, "onSerialConnectError: " + "connection failed: " + e.getMessage());
//        Toast.makeText(getContext(), "Connection Failed", Toast.LENGTH_SHORT).show();
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        Log.e(TAG, "onSerialIoError: " + "connection lost: " + e.getMessage());
        disconnect();
//        dismiss();
    }


    @Override
    public void onStart() {
        super.onStart();
        if (AppConstants.service != null)
            AppConstants.service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void OnDeviceClicked(String deviceAdd) {
        deviceAddress = deviceAdd;
        AppConstants.deviceAddress = deviceAdd;
        getActivity().runOnUiThread(this::connect);
    }
}