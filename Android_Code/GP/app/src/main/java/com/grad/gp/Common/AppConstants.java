package com.grad.gp.Common;

import android.graphics.Bitmap;

import com.grad.gp.Bluetooth.SerialService;

public class AppConstants {
    public static String BASE_URL = "https://gp-fcai-all.herokuapp.com/";

    public static enum Connected {False, Pending, True}

    public static Connected connected = Connected.False;

    public static Bitmap bluetoothImageBitMap;

    public static String deviceAddress = "";

    public static SerialService service;

    public static boolean isGlassesEnabled = false;
}
