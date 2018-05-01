package com.ndroid.atservice;

import android.app.NotificationManager;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.ndroid.atmanager.AntiTheftManager;
import com.ndroid.atmanager.IAntiTheftService;
import com.ndroid.atservice.models.DeviceLocation;
import com.ndroid.atservice.models.DeviceStatus;
import com.ndroid.atservice.server.AddDeviceTask;
import com.ndroid.atservice.server.GetDeviceStatusTask;
import com.ndroid.atservice.server.SendDeviceStatusTask;
import com.ndroid.atservice.server.SendLocationTask;
import com.ndroid.atservice.server.ServerApi;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static android.content.Context.LOCATION_SERVICE;
import static android.net.wifi.WifiManager.EXTRA_WIFI_STATE;
import static android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static com.ndroid.atservice.Constants.DEVICE_REGISTERED;
import static com.ndroid.atservice.Constants.DEVICE_REGISTERED_EXTRA_KEY;
import static com.ndroid.atservice.Constants.IP;
import static com.ndroid.atservice.Constants.RING_TIMEOUT;
import static com.ndroid.atservice.Constants.SERVER_URL;
import static com.ndroid.atservice.Constants.SERVER_URL_PREFIX;
import static com.ndroid.atservice.Constants.SERVER_URL_SUFFIX;

public class AntiTheftBinder extends IAntiTheftService.Stub {
    public static final String TAG = "AT_AntiTheftBinder";

    private Context mContext;
    private LocationManager mLocationManager;
    private AtomicInteger ANTI_THEFT_CHECK_FREQUENCY = new AtomicInteger(100000);
    private AtomicInteger LOCATION_REFRESH_FREQUENCY = new AtomicInteger(0);
    private int LOCATION_REFRESH_DISTANCE = 50;

    // Admin Device Manager
    private AdminReceiver mAdminReceiver;
    private DevicePolicyManager mDeviceManager ;

    // Wifi
    WifiManager mWifiManager;
    private AtomicBoolean mWifiState = new AtomicBoolean();
    private AtomicBoolean mShouldGetStatus = new AtomicBoolean();

    // Anti Theft Check
    private Handler mAntiTheftHandler;
    private HandlerThread mAntiTheftHandlerThread = null;
    private Runnable mGetDeviceStatusRunnable = new Runnable() {
        @Override
        public void run() {

            mWifiState.set(mWifiManager.isWifiEnabled());
            if (!mWifiState.get()) {
                // Turn Wifi on and get status
                mWifiManager.setWifiEnabled(true);
                Log.d(TAG, "Wifi Disabled, Enable & Postpone getDeviceStatus()");
                mShouldGetStatus.set(true);
            } else {
                // Wifi already On, Get status
                getDeviceStatus(true);
            }

            mAntiTheftHandler.postDelayed(this, ANTI_THEFT_CHECK_FREQUENCY.get());
        }
    };

    // Location
    private Handler mLocationHandler;
    private HandlerThread mLocationHandlerThread = null;
    private Runnable mLocationUpdateRunnable = new Runnable() {
        @Override
        public void run() {

            // Get Current DeviceLocation
            Location location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            // Get Current Time
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String time = sdf.format(new Date());

            // DeviceLocation to send to server
            DeviceLocation devLoc = new DeviceLocation();
            devLoc.setDeviceId(ServerApi.getCurrentDeviceId());
            devLoc.setLat(location.getLatitude());
            devLoc.setLon(location.getLongitude());
            devLoc.setTimeStamp(time);

            new SendLocationTask(new SendLocationTask.SendLocationCallback() {
                @Override
                public void onStarted() {
                }
                @Override
                public void onFinished(Boolean result) {
                }
            }).execute(devLoc);

            mLocationHandler.postDelayed(this, LOCATION_REFRESH_FREQUENCY.get());
        }
    };

    public AntiTheftBinder(Context c) {
        Log.d(TAG, "AntiTheftBinder created");
        mContext = c;

        AntiTheftManager.getInstance(mContext);

        mDeviceManager = (DevicePolicyManager)mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mAdminReceiver = new AdminReceiver();
        requestAdminPermissions();

        mLocationManager = (LocationManager)mContext.getSystemService(LOCATION_SERVICE);
        mWifiManager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        registerWifiReceiver();
        //Build URL server
        IP = Utils.getIpAddress(mContext);
        SERVER_URL = SERVER_URL_PREFIX + IP + SERVER_URL_SUFFIX;

        ANTI_THEFT_CHECK_FREQUENCY.set(Utils.getAtFrequency(mContext) * 1000);

        if (canStartAntiTheft()) {
            // Enable AntiTheft periodic check
            startAntiTheftThread();
        } else {
            Log.d(TAG, "Id Or Frequency not set");
        }
    }

    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        unregisterWifiReceiver();
        stopAntiTheftThread();
        stopLocationThread();
    }

    private void startLocationThread() {
        Log.d(TAG, "startLocationThread()");
        mLocationHandlerThread = new HandlerThread("Location_Thread");
        mLocationHandlerThread.start();
        mLocationHandler = new Handler(mLocationHandlerThread.getLooper());
        mLocationHandler.postDelayed(mLocationUpdateRunnable, LOCATION_REFRESH_FREQUENCY.get());
    }

    private void stopLocationThread() {
        Log.d(TAG, "stopLocationThread()");
        if (mLocationHandlerThread != null) {
            mLocationHandlerThread.quit();
            mLocationHandlerThread = null;
        }

        if (mLocationHandler != null) {
            mLocationHandler.removeCallbacksAndMessages(null);
            mLocationHandler = null;
        }
    }

    private void startAntiTheftThread() {
        Log.d(TAG, "startAntiTheftThread()");
        mAntiTheftHandlerThread = new HandlerThread("AT_Thread");
        mAntiTheftHandlerThread.start();
        mAntiTheftHandler = new Handler(mAntiTheftHandlerThread.getLooper());
        mAntiTheftHandler.post(mGetDeviceStatusRunnable);
    }

    private void stopAntiTheftThread() {
        Log.d(TAG, "stopAntiTheftThread()");
        if (mAntiTheftHandlerThread != null) {
            mAntiTheftHandlerThread.quit();
            mAntiTheftHandlerThread = null;
        }

        if (mAntiTheftHandler != null) {
            mAntiTheftHandler.removeCallbacksAndMessages(null);
            mAntiTheftHandler = null;
        }
    }

    private void sendDeviceStatus(DeviceStatus deviceStatus, final boolean previousWifiState) {
        new SendDeviceStatusTask(new SendDeviceStatusTask.SendDeviceStatusCallback() {
            @Override
            public void onStarted() {
            }

            @Override
            public void onFinished(Boolean result) {
                // Check if wifi state needs to be reverted
                if (!previousWifiState) {
                    disableWifi();
                }
            }
        }).execute(deviceStatus);
    }

    private void disableWifi() {
        Boolean enabled = mWifiManager.isWifiEnabled();
        if (!enabled) {
            Log.d(TAG, "Wifi Already disabled");
        } else {
            Log.d(TAG, "Disabling Wifi");
            mWifiManager.setWifiEnabled(false);
        }
    }

    public void enableAntiTheft() {
        Log.d(TAG, "enableAntiTheft()");
        if (canStartAntiTheft()) {
            // Enable AntiTheft periodic check
            startAntiTheftThread();
        } else {
            Log.e(TAG,"enableAntiTheft - failed");
        }
    }

    public void disableAntiTheft() {
        Log.d(TAG, "disableAntiTheft()");
        stopLocationThread();
        stopAntiTheftThread();
    }

    private boolean canStartAntiTheft() {
        boolean enabled = Utils.getAntiTheftStatus(mContext);
        int deviceId = Utils.getDeviceId(mContext);
        return enabled && deviceId != 0 && ANTI_THEFT_CHECK_FREQUENCY.get() != 0;
    }

    /**
     * Server API
     */

    private void getDeviceStatus(final boolean previousWifiState) {
        new GetDeviceStatusTask(new GetDeviceStatusTask.GetDeviceStatusCallback() {
            @Override
            public void onStarted() {
            }

            @Override
            public void onFinished(DeviceStatus status) {
                if (status == null) {
                    return;
                }

                // Start Location Thread - executed one time only
                if (LOCATION_REFRESH_FREQUENCY.get() == 0 && status.getLocationFrequency() != 0) {
                    Log.d(TAG, "Initiate Location Service");
                    LOCATION_REFRESH_FREQUENCY.set(status.getLocationFrequency() * 1000);
                    startLocationThread();
                }

                // Check if location frequency changed
                if (status.getLocationFrequency() != LOCATION_REFRESH_FREQUENCY.get() / 1000) {
                    LOCATION_REFRESH_FREQUENCY.set(status.getLocationFrequency() * 1000);
                    Log.d(TAG, "Location Frequency Changed To " + LOCATION_REFRESH_FREQUENCY.get());

                    // Restart location update handler
                    stopLocationThread();
                    if (LOCATION_REFRESH_FREQUENCY.get() != 0) {
                        startLocationThread();
                    } else {
                        Log.d(TAG, "Stopping location updates..");
                    }
                }

                // Check for pending device operations
                if (status.getTriggered() == 0) {
                    if (status.getEncryptStorage() == 1) {
                        // Encrypt Storage
                        encrypt();
                    } else {
                        // Decrypt Storage
                        decript();
                    }

                    if (status.getLock() == 1) {
                        // Lock Device
                        lock(true);
                    }

                    if (status.getReboot() == 1) {
                        // Reboot;
                        Log.d(TAG, "Should Reboot Device");
                    }

                    if (status.getWipeData() == 1) {
                        // Wipe data;
                        wipe(true);
                    }

                    if (status.getRing() == 1) {
                        // Ring
                        Log.d(TAG, "Should Ring");
                        ring(true);
                    } else {
                        ring(false);
                    }

                    // Operations triggered
                    status.setTriggered(1);

                    // Send Device Status and Revert wifi state
                    sendDeviceStatus(status, previousWifiState);
                } else {
                    if (!previousWifiState) {
                        disableWifi();
                    }
                }

            }
        }).execute(Utils.getDeviceId(mContext));
    }

    /**
     * Wifi Receiver
     */

    private BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }

            if (intent.getAction() == WIFI_STATE_CHANGED_ACTION) {
                int status = intent.getIntExtra(EXTRA_WIFI_STATE, 0);
                if (status == WIFI_STATE_ENABLED) {
                    Log.d(TAG, "on Receive WIFI_STATE_ENABLED");
                    if (mShouldGetStatus.compareAndSet(true,false)) {
                        getDeviceStatus(mWifiState.get());
                    }
                } else if (status == WIFI_STATE_DISABLED) {
                    Log.d(TAG, "on Receive WIFI_STATE_DISABLED");
                }

            }
        }
    };

    private void registerWifiReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WIFI_STATE_CHANGED_ACTION);
        mContext.registerReceiver(mWifiReceiver, filter);
    }

    private void unregisterWifiReceiver() {
        mContext.unregisterReceiver(mWifiReceiver);
    }

    /**
     * Device Admin Policies
     */
    private void requestAdminPermissions() {
        if (!isAdminActive()) {
            mContext.startActivity(new Intent(mContext, PermissionActivity.class));
        }
    }

    private boolean isAdminActive() {
        ComponentName receiver = new ComponentName(mContext, AdminReceiver.class);
        return mDeviceManager.isAdminActive(receiver);
    }

    /**
     * Remote functions
     */
    @Override
    public void encryptStorage(boolean status) {
        if (status) {
            encrypt();
        } else {
            decript();
        }
    }


    @Override
    public void lock(boolean status) {
        Log.d(TAG, "[ LOCK ]" + status);
        if(status) {
            if (isAdminActive()) {
                mDeviceManager.lockNow();
            } else {
                Log.e(TAG, "No Admin Permission");
                requestAdminPermissions();
            }
        }
    }
    @Override
    public void wipe(boolean status) {
        Log.d(TAG, "[ WIPE ]" + status);
        if (status) {
            if (isAdminActive()) {
                mDeviceManager.wipeData(0);
            } else {
                Log.e(TAG, "No Admin Permission");
                requestAdminPermissions();
            }
        }
    }

    @Override
    public void reboot(boolean status) {
        Log.d(TAG, "[ REBOOT ] " + status);
        if(status) {
            ComponentName receiver = new ComponentName(mContext, AdminReceiver.class);
            if (isAdminActive()) {
                mDeviceManager.reboot(receiver);
            } else {
                Log.e(TAG, "No Admin Permission");
                requestAdminPermissions();
            }
        }
    }

    public void encrypt() {
        Log.d(TAG, "[ ENCRYPT ]");
        ComponentName receiver = new ComponentName(mContext, AdminReceiver.class);
        if (isAdminActive()) {
            mDeviceManager.setStorageEncryption(receiver, true);
        } else {
            Log.e(TAG, "No Admin Permission");
            requestAdminPermissions();
        }
    }

    public void decript() {
        Log.d(TAG, "[ DECRYPT ]");
        ComponentName receiver = new ComponentName(mContext, AdminReceiver.class);
        if (isAdminActive()) {
            mDeviceManager.setStorageEncryption(receiver, false);
        } else {
            Log.e(TAG, "No Admin Permission");
            requestAdminPermissions();
        }
    }
    @Override
    public void ring(boolean status) {
        Log.d(TAG, "[ RING ] " + status);

        // Request Access to bypass "Do not disturb" mode
        NotificationManager n = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if(!n.isNotificationPolicyAccessGranted()) {
            Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            mContext.startActivity(intent);
            return;
        }

        // Set volume to max on STREAM_RING
        AudioManager audioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_RING,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_RING),0);

        // Play ringtone
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        final Ringtone r = RingtoneManager.getRingtone(mContext, alarm);
        if (status) {
            if (!r.isPlaying()) {
                Log.d(TAG, "Starting Ring..");
                r.play();

                // Stop after a timeout
                new Handler(). postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        r.stop();
                        Log.d(TAG, "Stopping Ring..");
                    }
                }, RING_TIMEOUT * 1000);
            }
        } else {
            if (r.isPlaying()) {
                r.stop();
                Log.d(TAG, "Stopping Ring..");
            }
        }
    }

    ////////////////////////////////////////////
    ////////////// Remote Methods /////////////


    /**
     * Device Id
     */
    @Override
    public int getDeviceId() {
        Integer id = Utils.getDeviceId(mContext);
        Log.d(TAG, "getDeviceId() " + id);
        return id;
    }
    @Override
    public void setDeviceId(int id) {
        Log.d(TAG, "setDeviceId() " + id);
        Utils.storeDeviceId(mContext, id);
    }

    /**
     * Device Name
     */
    @Override
    public String getDeviceName() {
        String name = Utils.getDeviceName(mContext);
        Log.d(TAG, "getDeviceName() " + name);
        return name;
    }

    @Override
    public void setDeviceName(String name) {
        Log.d(TAG, "setDeviceName() " + name);
        Utils.storeDeviceName(mContext, name);
    }

    /**
     * Device Pass
     */
    @Override
    public String getDevicePass() {
        String pass = Utils.getDevicePass(mContext);
        Log.d(TAG, "getDevicePass() " + pass);
        return pass;
    }

    @Override
    public void setDevicePass(String pass) {
        Log.d(TAG, "setDevicePass() " + pass);
        Utils.storeDevicePass(mContext, pass);
    }

    /**
     *  AntiTheft Status
     */

    @Override
    public boolean getAntiTheftStatus() {
        Boolean status = Utils.getAntiTheftStatus(mContext);
        Log.d(TAG, "getAntiTheftStatus() " + status);
        return status;
    }

    @Override
    public void setAntiTheftStatus(boolean status) {
        Log.d(TAG, "setAntiTheftStatus " + status);
        Utils.storeAntiTheftStatus(mContext, status);
        if (status) {
            enableAntiTheft();
        } else {
            disableAntiTheft();
        }
    }

    /**
     *  IP Address
     */
    @Override
    public String getIpAddress() {
        String ip = Utils.getIpAddress(mContext);
        Log.d(TAG, "getIpAddress() " +ip);
        return ip;
    }

    @Override
    public void setIpAddress(String ip) {
        Log.d(TAG, "setIpAddress() " +ip);
        Utils.storeIpAddress(mContext, ip);
        IP = ip;
        SERVER_URL = SERVER_URL_PREFIX + IP + SERVER_URL_SUFFIX;

        // restart service
        disableAntiTheft();
        enableAntiTheft();
    }

    /**
     *  AntiTheft Frequency
     */
    @Override
    public int getAtFrequency() {
        Integer fr = Utils.getAtFrequency(mContext);
        Log.d(TAG, "getAtFrequency() " + fr);
        return fr;
    }

    @Override
    public void setAtFrequency(int frequency) {
        Log.d(TAG, "setAtFrequency() " + frequency);
        Utils.storeAtFrequency(mContext, frequency);

        ANTI_THEFT_CHECK_FREQUENCY.set(frequency * 1000);
        // Restart service
        disableAntiTheft();
        enableAntiTheft();
    }

    @Override
    public void registerDevice(final String name, final String pass) {
        Log.d(TAG, "registerDevice() " + name + ", " + pass);
        new AddDeviceTask(new AddDeviceTask.AddDeviceCallback() {
            @Override
            public void onStarted() {
            }

            @Override
            public void onFinished(int id) {
                Log.d(TAG, "Registered Id :" + id);

                if (id == 0) {
                    Log.e(TAG, "Registration failed!");
                } else {
                    setDeviceId(id);
                    setDeviceName(name);
                    setDevicePass(pass);
                    setAntiTheftStatus(true);
                }

                Intent intent = new Intent(DEVICE_REGISTERED);
                intent.putExtra(DEVICE_REGISTERED_EXTRA_KEY, id);
                mContext.sendBroadcast(intent);
            }
        }).execute(name, pass);
    }
}
