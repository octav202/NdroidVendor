package com.ndroid.atservice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import com.ndroid.atmanager.AntiTheftManager;

import static com.ndroid.atservice.Constants.AT_SERVICE;

public class AntiTheftService extends Service {
    public static final String TAG = "AT_Service";

    private Context mContext;
    private AntiTheftBinder mBinder;

    public AntiTheftService() {
        super();
    }

    public AntiTheftService(Context context) {
        super();
        mContext = context;

        Log.d(TAG, "AntiTheftService - Created");
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("AT_", "onStartCommand()");
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("AT_", "onCreate()");
        mBinder = new AntiTheftBinder(getApplicationContext());

        // Bind Manager to Service
        AntiTheftManager.getInstance(getApplicationContext());

//        ServiceManager.addService(AT_SERVICE, mBinder);
//        Log.d(TAG, "AT_SERVICE - added to ServiceManager");
    }

}