package com.ndroid.atmanager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;



public class AntiTheftManager {

    public static final String TAG = "AT_Manager";
    private static final String ANTI_THEFT_SERVICE_CLASS = "at_service";
    private static AntiTheftManager sInstance;
    private static Context mContext;

    IAntiTheftService mService;
    private String action = "ANTI_THEFT_SERVICE_ACTION";
    private String packageName = "com.ndroid.atservice";

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mService = IAntiTheftService.Stub.asInterface(iBinder);
            Log.d(TAG, "onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    public static AntiTheftManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AntiTheftManager(context);
        }

        return sInstance;
    }

    private AntiTheftManager(Context context) {
        mContext = context;
        Intent serviceIntent = new Intent(action);
        serviceIntent.setPackage(packageName);
        context.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Public Methods
     */

    public void setValue(int val) {
        Log.d(TAG, "setValue() : " + val);
        if (mService != null) {
            try {
                mService.setValue(val);
            } catch (RemoteException e) {
                Log.e(TAG, "setValue() Exception " + e.getLocalizedMessage());
            }
        } else {
            Log.e(TAG, "Service is null");
        }

//        IAntiTheftService binder = IAntiTheftService.Stub.asInterface(
//                ServiceManager.getService(ANTI_THEFT_SERVICE_CLASS));
//
//        if (binder == null) {
//            Log.d(TAG, "Error - Null Service");
//            return;
//        }
//        try {
//            binder.setValue(20);
//            Log.d(TAG, "Service called succesfully");
//        } catch (Exception e) {
//            Log.d(TAG, "Exception: " + e.getLocalizedMessage());
//        }
    }
}
