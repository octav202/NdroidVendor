package com.ndroid.atservice;

import android.content.Context;
import android.util.Log;

import com.ndroid.atmanager.IAntiTheftService;

public class AntiTheftBinder extends IAntiTheftService.Stub {
    public static final String TAG = "AT_AntiTheftBinder";

    private Context mContext;
    public AntiTheftBinder(Context c) {
        Log.d(TAG, "AntiTheftBinder created");
        mContext = c;
    }

    // Device Id
    @Override
    public int getDeviceId() {
        Log.d(TAG, "getDeviceId()");
        return 0;
    }

    @Override
    public void setDeviceId(int id) {
        Log.d(TAG, "setDeviceId() " +id );
    }

    // Device Name
    @Override
    public String getDeviceName() {
        Log.d(TAG, "getDeviceName()");
        return "";
    }

    @Override
    public void setDeviceName(String name){
        Log.d(TAG, "setDeviceName() " + name);
    }

    // Device Pass
    @Override
    public String getDevicePass() {
        Log.d(TAG, "getDevicePass()");
        return "";
    }

    @Override
    public void setDevicePass(String pass) {
        Log.d(TAG, "setDevicePass() " + pass);
    }

    // AntiTheft Status
    @Override
    public boolean getAntiTheftStatus() {
        Log.d(TAG, "getAntiTheftStatus()");
        return false;
    }

    @Override
    public void setAntiTheftStatus(boolean status) {
        Log.d(TAG, "setAntiTheftStatus() " + status);
    }

    // IP Address
    @Override
    public String getIpAddress() {
        Log.d(TAG, "getIpAddress()");
        return "";
    }

    @Override
    public void setIpAddress(String ip) {
        Log.d(TAG, "setIpAddress() " + ip);
    }

    // AntiTheft Frequency
    @Override
    public int getAtFrequency() {
        Log.d(TAG, "getAtFrequency()");
        return 0;
    }

    @Override
    public void setAtFrequency(int frequency) {
        Log.d(TAG, "setAtFrequency() " + frequency);
    }
}
