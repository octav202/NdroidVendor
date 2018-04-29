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

    @Override
    public void setValue(int val) {
        Log.d(TAG, "setValue() " + val);
    }
}
