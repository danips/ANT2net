package com.quantrity.ant2net;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.dsi.ant.AntSupportChecker;
import com.dsi.ant.plugins.antplus.common.AntFsCommon;
import com.dsi.ant.plugins.antplus.common.FitFileCommon;
import com.dsi.ant.plugins.antplus.pcc.AntPlusWatchDownloaderPcc;
import com.dsi.ant.plugins.antplus.pcc.defines.AntFsRequestStatus;
import com.dsi.ant.plugins.antplus.pcc.defines.AntFsState;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;
import com.garmin.fit.ActivityMesg;
import com.garmin.fit.ActivityMesgListener;
import com.garmin.fit.BatteryMesg;
import com.garmin.fit.BatteryMesgListener;
import com.garmin.fit.Decode;
import com.garmin.fit.Field;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.FileIdMesgListener;
import com.garmin.fit.Mesg;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.MesgListener;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.SessionMesgListener;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";
    private final static String FIRST_TIME = "first_time";
    static final int SETTINGS_REQUEST = 12345;
    public static final int EX_DIRECTORY_PICKER_RESULT = 102;
    public static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 110;

    AntPlusWatchDownloaderPcc watchPcc = null;
    PccReleaseHandle<AntPlusWatchDownloaderPcc> releaseHandle = null;

    ProgressDialog antFsProgressDialog;
    ProgressBar antProgressBar;
    ProgressBar batteryProgressBar;
    TextView batteryTV;
    List<SportActivity> activitiesList;

    String deviceName;
    short battery = 101;
    short first_battery = 0;
    long battery_when = 0;
    float battery_duration;


    private static ActivityAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private ArrayList<SportActivity> input;
    private String deviceListAccessResultCode = "";

    private boolean first_time, pro_first_time, copyActivities = false;

    private int DEBUG_LEVEL = 3;

    public boolean isPackageUninstalled(String packageName) {
        try{
            getApplicationContext().getPackageManager().getPackageInfo(packageName, PackageManager.GET_SERVICES);
            return false;
        } catch(PackageManager.NameNotFoundException e ){
            return true;
        }
    }

    public void goToPermissions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getResources().getString(R.string.msg_problem_ant_permission_disabled))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:com.dsi.ant.service.socket"));
                        intent.addCategory(Intent.CATEGORY_DEFAULT);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        dialog.cancel();
                        finish();
                    }
                }).setCancelable(false).create().show();
    }

    private void checkPermission() {
        //Check ANT Radio Service permissions
        if (Build.VERSION.SDK_INT >= 16) {
            try {
                PackageInfo pi = getApplicationContext().getPackageManager().getPackageInfo("com.dsi.ant.service.socket", PackageManager.GET_PERMISSIONS);
                final String[] requestedPermissions = pi.requestedPermissions;
                boolean enabled = false;
                for (int i = 0, len = requestedPermissions.length; i < len; i++) {

                    if (requestedPermissions[i].startsWith("com.dsi.ant.permission.ANT") &&
                            ((pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0))
                    {
                        enabled = true;
                        break;
                    }
                }
                if (!enabled)
                {
                    goToPermissions();
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    void createSDFolderAndCopyActivities()
    {
        if (input == null)
        {
            copyActivities = true;
            return;
        }
        /* Create folder in SD */
        String filepath = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("backup_folder", "");
        if (!filepath.equals("") && !input.isEmpty()) {
            File backup_dir = new java.io.File(filepath);
            boolean res = backup_dir.mkdirs();
            if (Debug.ON) Log.v(TAG, "dir=" + backup_dir + " res=" + res);
            if (res)
            {
                if (pro_first_time)
                {
                    for (SportActivity sa : input) {
                        try {
                            InputStream in = new FileInputStream(new File(getFilesDir() + "/fits/", sa.toString()));
                            OutputStream out = new FileOutputStream(backup_dir + "/" + sa.getDateString() + ".fit");
                            // Transfer bytes from in to out
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        copyActivities = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MainActivity.REQUEST_CODE_WRITE_EXTERNAL_STORAGE)
        {
            if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
            {
                createSDFolderAndCopyActivities();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        //if (Debug.ON) Log.v(TAG,"ANT2net PRO v" + BuildConfig.VERSION_NAME);

        File backup_dir = null;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        pro_first_time = first_time = prefs.getBoolean(FIRST_TIME, true);
        if (first_time) {
            prefs.edit().putBoolean(FIRST_TIME, false).commit();
            /* Checks if external storage is available for read and write */
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                String backup_folder = Environment.getExternalStorageDirectory() + "/ant2net";
                PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putString("backup_folder", backup_folder).apply();

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    copyActivities = true;
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MainActivity.REQUEST_CODE_WRITE_EXTERNAL_STORAGE);
                }
            }
            try {
                Context friendContext = this.createPackageContext("com.quantrity.ant2net", Context.CONTEXT_IGNORE_SECURITY);

                Map<String, ?> allEntries = PreferenceManager.getDefaultSharedPreferences(friendContext).getAll();
                SharedPreferences.Editor editor = prefs.edit();
                for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                    if (entry.getValue() instanceof Boolean) {
                        editor.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                    }
                    else if (entry.getValue() instanceof String) editor.putString(entry.getKey(), (String)entry.getValue());
                }
                editor.commit();

                java.io.File cache = new java.io.File(friendContext.getFilesDir() + "/fits");
                java.io.File[] files = cache.listFiles();
                if (files != null) {
                    File folder = new File(getFilesDir() + "/fits");
                    boolean success = true;
                    if (!folder.exists()) {
                        success = folder.mkdirs();
                    }
                    if (success) {
                        for (final java.io.File fileEntry : files) {
                            try {
                                InputStream in = new FileInputStream(fileEntry);
                                OutputStream out = new FileOutputStream(getFilesDir() + "/fits/" + fileEntry.getName());
                                // Transfer bytes from in to out
                                byte[] buf = new byte[1024];
                                int len;
                                while ((len = in.read(buf)) > 0) {
                                    out.write(buf, 0, len);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        boolean going_to_market = false;
        if (isPackageUninstalled("com.dsi.ant.service.socket")) {
            goToMarket("com.dsi.ant.service.socket", String.format(getString(R.string.msg_problem_service_not_found), getString(R.string.ant_radio_service)));
            going_to_market = true;
        } else if (isPackageUninstalled("com.dsi.ant.plugins.antplus")) {
            goToMarket("com.dsi.ant.plugins.antplus", String.format(getString(R.string.msg_problem_service_not_found), getString(R.string.ant_plugins_service)));
            going_to_market = true;
        } else if (!AntSupportChecker.hasAntFeature(this)) {
            if (isPackageUninstalled("com.dsi.ant.usbservice")) goToMarket("com.dsi.ant.usbservice", String.format(getString(R.string.msg_problem_service_not_found), getString(R.string.ant_usb_service)));
            going_to_market = true;
        }

        if ((pro_first_time) && (!going_to_market)) {
            PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.msg_first_time)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), SETTINGS_REQUEST);
                            dialog.cancel();
                        }
                    }).create().show();
        }

        RecyclerView mRecyclerView = (RecyclerView)findViewById(R.id.recycler_view);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        //Read existing activities
        //final Object data = getLastNonConfigurationInstance();
        final Object data = getLastCustomNonConfigurationInstance();
        if (data == null) {
            if (Debug.ON) Log.v(TAG, "onCreate 1");
            input = new ArrayList<>();
            java.io.File cache = new java.io.File(getFilesDir() + "/fits");
            java.io.File[] files = cache.listFiles();
            if (files != null) {
                Arrays.sort(files, Collections.reverseOrder());
                for (final java.io.File fileEntry : files) {
                    if (!fileEntry.isDirectory()) {
                        Log.v(TAG, "Adding "  + fileEntry.getPath());
                        String[] parts = fileEntry.getName().split("_");

                        byte gc = 3, st = 3, tp=3, spt = 3, gd = 3, em = 3, dr = 3;
                        String training_effect = null;
                        boolean rename = false;
                        for (int i = 4; i < parts.length; i++) {
                            if (parts[i].startsWith("em")) {
                                if (parts[i].charAt(2) == '1') em = (byte) 1;
                                else if (parts[i].charAt(2) == '0') em = (byte) 0;
                                else {
                                    rename = true;
                                    em = (byte) 3;
                                }
                                //em = (byte) ((parts[i].charAt(2) == '1') ? 1 : 0);
                            }  else if (parts[i].startsWith("te")) {
                                training_effect = parts[i].substring(2);
                            }
                        }

                        SportActivity sa = new SportActivity(Short.parseShort(parts[1]),
                                Long.parseLong(parts[0]), Float.parseFloat(parts[2]) / 1000,
                                Float.parseFloat(parts[3]) / 1000, gc, st, tp, spt, gd, em, dr, (training_effect == null) ? -1f : Float.parseFloat(training_effect)/10);
                        if (rename) {
                            java.io.File file = new java.io.File(fileEntry.getPath());
                            java.io.File file2 = new java.io.File(getFilesDir() + "/fits", sa.toString());
                            file.renameTo(file2);
                        }
                        input.add(sa);
                    }
                }
            }
        } else {
            if (Debug.ON) Log.v(TAG, "onCreate 2");
            input = (ArrayList<SportActivity>) data;
        }
        if (copyActivities)
        {
            createSDFolderAndCopyActivities();
            copyActivities = false;
        }

        // specify an adapter
        mAdapter = new ActivityAdapter(input, MainActivity.this);
        mRecyclerView.setAdapter(mAdapter);

        View actionbar_extra = getLayoutInflater().inflate(R.layout.actionbar_extra, null);
        antProgressBar = actionbar_extra.findViewById(R.id.antProgressBar);
        antProgressBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (releaseHandle != null) releaseHandle.close();
                antProgressBar.setVisibility(View.INVISIBLE);
            }
        });
        batteryProgressBar = actionbar_extra.findViewById(R.id.batteryProgressBar);
        batteryTV = actionbar_extra.findViewById(R.id.batteryTV);

        getSupportActionBar().setDisplayShowCustomEnabled(true);
        getSupportActionBar().setCustomView(actionbar_extra);
        getSupportActionBar().setTitle(R.string.app_name);

        int battery = prefs.getInt("battery", 101);
        String battery_duration = prefs.getString("battery_duration", "");
        if (battery != 101) {
            this.battery = (short)battery;
            batteryProgressBar.setProgress(battery);
            batteryProgressBar.setVisibility(View.VISIBLE);
            batteryTV.setText(getString(R.string.actionbar_battery_abbreviation) + "\n" + battery + "%\n" + battery_duration);
            batteryTV.setVisibility(View.VISIBLE);
        }

        if ((data == null) && (!first_time || !pro_first_time) && (!going_to_market)) {
            resetPcc();
        }
    }

    public void goToMarket(final String pkg, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
                        } catch (android.content.ActivityNotFoundException e) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + pkg)));
                        }
                        dialog.cancel();
                        finish();
                    }
                }).setCancelable(false).create().show();
    }

    /**
     * Resets the PCC connection to request access again and clears any existing display data.
     */
    private void resetPcc() {
        //Release the old access if it exists
        if (releaseHandle != null) releaseHandle.close();

        antProgressBar.setVisibility(View.VISIBLE);

        //Make the access request
        releaseHandle = AntPlusWatchDownloaderPcc.requestDeviceListAccess(this, new AntPluginPcc.IPluginAccessResultReceiver<AntPlusWatchDownloaderPcc>() {
                    @Override
                    public void onResultReceived(AntPlusWatchDownloaderPcc result, RequestAccessResult resultCode, DeviceState initialDeviceState) {
                        if (Debug.ON) Log.v(TAG, "onResultReceived "+resultCode.name());
                        deviceListAccessResultCode += ((deviceListAccessResultCode.equals("")) ? "" : ", ") + resultCode.name();
                        switch(resultCode) {
                            case SUCCESS:
                                watchPcc = result;
                                if (Debug.ON) Log.v(TAG, result.getDeviceName() + ": " + initialDeviceState);
                                watchPcc.requestCurrentDeviceList();
                                break;
                            case CHANNEL_NOT_AVAILABLE:
                                if (Debug.ON) Log.v(TAG, "Channel Not Available");
                                if (Debug.ON) Log.v(TAG, "Error. Do Menu->Reset.");
                                break;
                            case ADAPTER_NOT_DETECTED:
                                if (Debug.ON) Log.v(TAG, "ANT Adapter Not Available. Built-in ANT hardware or external adapter required.");
                                if (Debug.ON) Log.v(TAG, "Error. Do Menu->Reset.");
                                checkPermission();
                                break;
                            case BAD_PARAMS:
                                //Note: Since we compose all the params ourself, we should never see this result
                                if (Debug.ON) Log.v(TAG, "Bad request parameters.");
                                if (Debug.ON) Log.v(TAG, "Error. Do Menu->Reset.");
                                break;
                            case OTHER_FAILURE:
                                if (Debug.ON) Log.v(TAG, "RequestAccess failed. See logcat for details.");
                                if (Debug.ON) Log.v(TAG, "Error. Do Menu->Reset.");
                                break;
                            case DEPENDENCY_NOT_INSTALLED:
                                if (Debug.ON) Log.v(TAG, "Error. Do Menu->Reset.");
                                goToMarket(AntPlusWatchDownloaderPcc.getMissingDependencyPackageName(),
                                        String.format(getString(R.string.msg_problem_service_not_found),
                                                AntPlusWatchDownloaderPcc.getMissingDependencyName()));
                                if (antFsProgressDialog != null) antFsProgressDialog.dismiss();
                                break;
                            case USER_CANCELLED:
                                if (Debug.ON) Log.v(TAG, "Cancelled. Do Menu->Reset.");
                                break;
                            case UNRECOGNIZED:
                                if (Debug.ON) Log.v(TAG, "Failed: UNRECOGNIZED. PluginLib Upgrade Required?");
                                break;
                            default:
                                if (Debug.ON) Log.v(TAG, "Unrecognized result: " + resultCode);
                                if (Debug.ON) Log.v(TAG, "Error. Do Menu->Reset.");
                                break;
                        }
                    }
                },
                //Receives state changes and shows it on the status display line
                new AntPluginPcc.IDeviceStateChangeReceiver() {
                    @Override
                    public void onDeviceStateChange(final DeviceState newDeviceState) {
                        if (Debug.ON) Log.v(TAG, "IDeviceStateChangeReceiver " + watchPcc.getDeviceName() + ": " + newDeviceState);

                        if (newDeviceState == DeviceState.DEAD) watchPcc = null;
                    }
                },
                //Receives the device list updates and displays the current list
                new AntPlusWatchDownloaderPcc.IAvailableDeviceListReceiver() {
                    @Override
                    public void onNewAvailableDeviceList(AntPlusWatchDownloaderPcc.DeviceListUpdateCode listUpdateCode,
                                                         final AntPlusWatchDownloaderPcc.DeviceInfo[] deviceInfos, final AntPlusWatchDownloaderPcc.DeviceInfo deviceChanging) {
                        if (Debug.ON) Log.v(TAG, "onNewAvailableDeviceList "+listUpdateCode.name());
                        switch(listUpdateCode) {
                            case NO_CHANGE:
                                break;
                            case DEVICE_ADDED_TO_LIST:
                                if (Debug.ON) Log.v(TAG, "DEVICE_ADDED_TO_LIST " + deviceChanging.getDisplayName());
                                if (deviceName == null) deviceName = deviceChanging.getDisplayName();

                                Timer timer = new Timer();
                                TimerTask delayedThreadStartTask = new TimerTask() {
                                    @Override
                                    public void run() {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                antFsProgressDialog = new ProgressDialog(MainActivity.this);
                                                antFsProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                                                antFsProgressDialog.setMessage("[" + deviceName + "]\n" + getString(R.string.antFsProgressDialog_sending_request));

                                                activitiesList = new ArrayList<>();

                                                String deviceUUID = deviceChanging.getDeviceUUID().toString();
                                                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                                                String string = preferences.getString(deviceUUID, null);

                                                if (string == null) {
                                                    if (Debug.ON) Log.v(TAG, "requestDownloadAllActivities");
                                                    if ((watchPcc != null) && (watchPcc.requestDownloadAllActivities(deviceChanging.getDeviceUUID(),
                                                            new DownloadActivitiesFinished(deviceUUID),
                                                            new FileDownloadedReceiver(),
                                                            new AntFsUpdateReceiver()))) {
                                                        antFsProgressDialog.show();
                                                    } else antFsProgressDialog.cancel();
                                                } else {// download new
                                                    if (Debug.ON) Log.v(TAG, "requestDownloadNewActivities");
                                                    if ((watchPcc != null) && (watchPcc.requestDownloadNewActivities(deviceChanging.getDeviceUUID(),
                                                            //if ((watchPcc != null) && (watchPcc.requestDownloadAllActivities(deviceChanging.getDeviceUUID(),
                                                            new DownloadActivitiesFinished(deviceUUID),
                                                            new FileDownloadedReceiver(),
                                                            new AntFsUpdateReceiver()))) {
                                                        antFsProgressDialog.show();
                                                    } else antFsProgressDialog.cancel();
                                                }
                                            }
                                        });
                                    }
                                };
                                timer.schedule(delayedThreadStartTask, 500);

                                break;
                            case DEVICE_REMOVED_FROM_LIST:
                                if (Debug.ON) Log.v(TAG, "DEVICE_REMOVED_FROM_LIST");
                                break;

                            case UNRECOGNIZED:
                                if (Debug.ON) Log.v(TAG, "Failed: UNRECOGNIZED. PluginLib Upgrade Required?");
                                break;
                        }
                    }
                });
        //releaseHandle.close();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_reset) {
            if (Debug.ON) Log.v(TAG, "Resetting...");
            resetPcc();
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_REQUEST);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (antFsProgressDialog != null) antFsProgressDialog.dismiss();
        if (releaseHandle != null) releaseHandle.close();
        super.onDestroy();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        //return super.onRetainCustomNonConfigurationInstance();
        return input;
    }

    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    /**
     * Handles receiving the download activities results, displaying the activity info on success.
     */
    private class DownloadActivitiesFinished implements AntPlusWatchDownloaderPcc.IDownloadActivitiesFinishedReceiver {
        String deviceUUID;

        DownloadActivitiesFinished(String deviceUUID) {
            this.deviceUUID = deviceUUID;
        }

        public void onNewDownloadActivitiesFinished(final AntFsRequestStatus status) {
            if (Debug.ON) Log.v(TAG, Calendar.getInstance().getTimeInMillis() + "_ onNewDownloadActivitiesFinished " + status.name());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    antFsProgressDialog.dismiss();

                    switch(status) {
                        case SUCCESS:
                            if(!activitiesList.isEmpty()) {
                                //Update battery status
                                if (battery != 101) {
                                    batteryProgressBar.setProgress(battery);
                                    batteryProgressBar.setVisibility(View.VISIBLE);
                                    if (Debug.ON) Log.v(TAG, "BATT CALC " + first_battery + " - " + battery + " - " + battery_duration);
                                    String duration = "";
                                    if (first_battery != battery) {
                                        float batt_diff = first_battery - battery + 1;
                                        float estimated_duration = (battery * battery_duration) / batt_diff;
                                        duration =  String.format("%d:%02d", ((int)(estimated_duration / 3600)), ((int)((estimated_duration % 3600) / 60)));
                                    }
                                    batteryTV.setText(getString(R.string.actionbar_battery_abbreviation) + "\n" + battery + "%\n" +  duration);
                                    batteryTV.setVisibility(View.VISIBLE);
                                    SharedPreferences.Editor e = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
                                    e.putInt("battery", battery);
                                    e.putString("battery_duration", duration);
                                    e.apply();
                                }

                                if (pro_first_time) {
                                    //Show dialog asking whether to upload or ignore
                                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                    builder.setMessage(R.string.msg_first_upload)
                                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    dialog.dismiss();
                                                    AsyncUpload au = new AsyncUpload(MainActivity.this, mAdapter, activitiesList);
                                                    au.execute();
                                                }
                                            })
                                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int i) {
                                                    dialog.dismiss();
                                                }
                                            }).create().show();
                                } else {
                                    if (!isOnline()) {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                        builder.setMessage(String.format(getString(R.string.antFsProgressDialog_no_internet), getString(android.R.string.yes)))
                                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int id) {
                                                        dialog.dismiss();
                                                        AsyncUpload au = new AsyncUpload(MainActivity.this, mAdapter, activitiesList);
                                                        au.execute();
                                                    }
                                                }).create().show();
                                    } else {
                                        AsyncUpload au = new AsyncUpload(MainActivity.this, mAdapter, activitiesList);
                                        au.execute();
                                    }
                                }

                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                                for (SportActivity a : activitiesList) {
                                    try {
                                        mAdapter.add(0, a);
                                        mLayoutManager.scrollToPosition(0);

                                        //Keep data in private folder
                                        java.io.File f = new java.io.File(getFilesDir() + "/fits", a.toString());
                                        f.getParentFile().mkdirs();
                                        FileOutputStream fos = new FileOutputStream(f);
                                        fos.write(a.getRawBytes());
                                        fos.close();
                                        if (Debug.ON) Log.v(TAG, "Saved internal file to " + f.getAbsolutePath());


                                        //Write file to sdcard
                                        String filepath = prefs.getString("backup_folder", "");
                                        if (Debug.ON) Log.v(TAG, "backup_folder=" + filepath + "; original=" + Environment.getExternalStorageDirectory() + "/ant2net" + " flag=" + Environment.MEDIA_MOUNTED + "_" + Environment.getExternalStorageState());
                                        if (!filepath.equals("")) {
                                            String filename = a.getDateString() + ".fit";

                                            if (filepath.endsWith("/")) filepath = filepath + filename;
                                            else filepath = filepath + "/" + filename;

                                            if (Debug.ON) Log.v(TAG, "filepath="+filepath);
                                            f = new java.io.File(filepath);
                                            boolean res = f.getParentFile().mkdirs();
                                            if (Debug.ON) Log.v(TAG, "file="+f+"res="+res);
                                            fos = new FileOutputStream(f);
                                            fos.write(a.getRawBytes());
                                            fos.close();
                                            if (Debug.ON) Log.v(TAG, "Saved file to " + filepath);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        if (Debug.ON) Log.v(TAG, "Error writing FIT file: " + e.getMessage());
                                    }
                                }

                                //Remember we have already downloaded all the activities
                                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putString(deviceUUID, "1");
                                editor.apply();
                            }


                            //Close the ANT service
                            //Log.v(TAG, "Releasing the ANT service");
                            if (releaseHandle != null) {
                                releaseHandle.close();
                                antProgressBar.setVisibility(View.INVISIBLE);
                                //setSupportProgressBarIndeterminateVisibility(false);
                            }

                            return;
                        case FAIL_ALREADY_BUSY_EXTERNAL:
                            if (Debug.ON) Log.v(TAG, "Download failed, device busy.");
                            break;
                        case FAIL_DEVICE_COMMUNICATION_FAILURE:
                            if (Debug.ON) Log.v(TAG, "Download failed, communication error.");
                            break;
                        case FAIL_AUTHENTICATION_REJECTED:
                            //NOTE: This is thrown when authentication has failed, most likely when user action is required to enable pairing
                            if (Debug.ON) Log.v(TAG, "Download failed, authentication rejected.");
                            break;
                        case FAIL_DEVICE_TRANSMISSION_LOST:
                            if (Debug.ON) Log.v(TAG, "Download failed, transmission lost.");
                            break;
                        case UNRECOGNIZED:
                            if (Debug.ON) Log.v(TAG, "Failed: UNRECOGNIZED. PluginLib Upgrade Required?");
                            break;
                        default:
                            break;
                    }
                }
            });
        }
    }

    private SportActivity parseActivity(InputStream is, String name) {
        final long[] tmp_start = {0};
        final long[] when = {0};
        final short[] sport = {0};
        final float[] duration = {0.0f};
        final float[] distance = {0.0f};
        final float[] training_effect = {0.0f};
        FileIdMesgListener fileIdMesgListener = new FileIdMesgListener() {
            @Override
            public void onMesg(final FileIdMesg mesg) {
                if ((DEBUG_LEVEL >= 2) && (Debug.ON)) {
                    Log.v(TAG, "=== FileIdMesgListener ===");
                    Collection<Field> fields = mesg.getFields();
                    for (Field field : fields)
                        Log.v(TAG, field.getName() + " = " + field.getValue() + "; units=" + field.getUnits());
                    Log.v(TAG, "=== /FileIdMesgListener ===");
                }
                when[0] = mesg.getTimeCreated().getDate().getTime();
                tmp_start[0] = mesg.getTimeCreated().getTimestamp() * 1000;
            }
        };

        SessionMesgListener sessionMesgListener = new SessionMesgListener() {
            @Override
            public void onMesg(final SessionMesg mesg) {
                if ((DEBUG_LEVEL >= 2) && (Debug.ON) ) {
                    Log.v(TAG, "=== SessionMesgListener ===");
                    Collection<Field> fields = mesg.getFields();
                    for (Field field : fields)
                        Log.v(TAG, field.getName() + " = " + field.getValue() + "; units=" + field.getUnits());
                    Log.v(TAG, "=== /SessionMesgListener ===");
                }

                if (mesg.getTotalElapsedTime() != null) duration[0] = mesg.getTotalElapsedTime();
                if (mesg.getSport() != null) sport[0] = mesg.getSport().getValue();
                if (mesg.getTotalDistance() != null) distance[0] = mesg.getTotalDistance() / 1000;
                if (mesg.getTotalTrainingEffect() != null) training_effect[0] = mesg.getTotalTrainingEffect();
            }
        };

        MesgListener msgListener = null;
        if ((DEBUG_LEVEL >= 3) && (Debug.ON)) {
            msgListener = new MesgListener() {
                @Override
                public void onMesg(Mesg mesg) {

                    if (!mesg.getName().equals("record")) {
                        Log.v(TAG, "=== MesgListener === " + mesg.getName());
                        Collection<Field> fields = mesg.getFields();
                        for (Field field : fields) {
                            Log.v(TAG, field.getName() + " = " + field.getValue() + "; units=" + field.getUnits());
                        }
                        Log.v(TAG, "=== /MesgListener ===");
                    }
                }
            };
        }

        BatteryMesgListener batteryMesgListener = new BatteryMesgListener() {
            @Override
            public void onMesg(BatteryMesg mesg) {
                if ((DEBUG_LEVEL >= 2) && (Debug.ON)) {
                    Log.v(TAG, "=== BatteryMesgListener === " + mesg.getName());
                    Collection<Field> fields = mesg.getFields();
                    for (Field field : fields) {
                        Log.v(TAG, field.getName() + " = " + field.getValue() + "; units=" + field.getUnits());
                    }
                    Log.v(TAG, "=== /BatteryMesgListener ===");
                }

                if (mesg.hasField(2)) {
                    if (Debug.ON) Log.v(TAG, when[0] + "_" + battery_when + "_" + mesg.getLevel());
                    if (when[0] > battery_when) {//first time found
                        battery_when = when[0];
                        first_battery = mesg.getLevel();
                        battery = mesg.getLevel();
                        //battery_duration = duration[0];
                        if (Debug.ON) Log.v(TAG, "First time " + when[0] + "-" + battery);
                    } else if (when[0] == battery_when) {//following readings
                        battery = mesg.getLevel();
                        //battery_duration = duration[0];
                        if (Debug.ON) Log.v(TAG, "Next time " + when[0] + "-" + battery);
                    } else if (Debug.ON) Log.v(TAG, "NOT Saved");
                }
            }
        };

        ActivityMesgListener activityMesgListener = new ActivityMesgListener() {
            @Override
            public void onMesg(ActivityMesg mesg) {
                if (Debug.ON) Log.v(TAG, "Start timestamp is " + when[0]);
                if (when[0] < 946684801000L) //1 Jan 2000 00:00:01 GMT
                {
                    if (Debug.ON)
                    {
                        Log.v(TAG, "Start timestamp too short, set to " + when[0]);
                        Log.v(TAG, 631065600000L + " _ " + (mesg.getLocalTimestamp() * 1000) + " _ "+ tmp_start[0]);
                    }
                    when[0] = 631065600000L + mesg.getLocalTimestamp() * 1000 + tmp_start[0];
                }
            }
        };


        MesgBroadcaster mesgBroadcaster = new MesgBroadcaster();
        mesgBroadcaster.addListener(fileIdMesgListener);
        mesgBroadcaster.addListener(sessionMesgListener);
        mesgBroadcaster.addListener(batteryMesgListener);
        mesgBroadcaster.addListener(activityMesgListener);

        if ((DEBUG_LEVEL >= 3) && (Debug.ON)) {
            mesgBroadcaster.addListener(msgListener);
        }
        if (Debug.ON) Log.v(TAG, "Begin decoding file (" + name + ")");
        boolean gotException = false;
        try {
            mesgBroadcaster.run(is);
        } catch (Exception e) {
            if (Debug.ON) Log.v(TAG, "Decoding Exception " + e.getMessage());
            gotException = true;
        }
        if (Debug.ON) Log.v(TAG, "End decoding file " + name);
        if (!gotException) {
            if (battery_when == when[0]) battery_duration = duration[0];
            if (Debug.ON)
                Log.v(TAG, "Last time " + when[0] + "-" + battery + "-" + battery_duration);
            return new SportActivity(sport[0], when[0], distance[0], duration[0], training_effect[0]);
        } else {
            return null;
        }
    }

    /**
     * Stores the downloaded files in a list until the entire request is finished.
     */
    private class FileDownloadedReceiver implements FitFileCommon.IFitFileDownloadedReceiver {

        public void onNewFitFileDownloaded(FitFileCommon.FitFile downloadedFitFile) {
            if (downloadedFitFile == null) {
                if (Debug.ON) Log.v(TAG, "downloadedFitFile is null");
                return;
            }
            try {
                if (downloadedFitFile.getInputStream() == null) {
                    if (Debug.ON) Log.v(TAG, "downloadedFitFile InputStream is null");
                    return;
                }
            } catch (Exception e)
            {
                if (Debug.ON) Log.v(TAG, "downloadedFitFile InputStream is null(2)");
                return;
            }
            if ((Debug.ON) && (downloadedFitFile.getRawBytes() != null))
                Log.v(TAG, Calendar.getInstance().getTimeInMillis() + " _ Received FIT file of size " + downloadedFitFile.getRawBytes().length);
            InputStream fitFile = downloadedFitFile.getInputStream();

            if((fitFile == null) || !Decode.checkIntegrity(fitFile) || (downloadedFitFile.getRawBytes() == null)) {
                if (Debug.ON) Log.v(TAG, "FIT file integrity check failed.");
                return;
            }

            // NOTE: This sampler app caches all download data and performs all processing when the file transfer
            // session has finished.  If dealing with larger files or a large number of files, it
            // may be better to store the files on the file system instead of just keeping them in memory.

            // Realizar la decodificacion aqui
            // La descarga continua, pero no se recibe el siguiente fichero hasta que finaliza este método
            // Lo mismo pasa para el evento de descarga completa, hasta que las sucesivas llamadas a este método no acaban, no salta
            SportActivity sa = parseActivity(downloadedFitFile.getInputStream(), downloadedFitFile.getRawBytes().length + "");
            if (sa != null) {
                sa.setRawBytes(downloadedFitFile.getRawBytes());
                activitiesList.add(sa);
            }
        }
    }

    /**
     * Handles displaying the ANTFS status and progress in the progress dialog.
     */
    private class AntFsUpdateReceiver implements AntFsCommon.IAntFsProgressUpdateReceiver {
        private AlertDialog pairingAD = null;

        public void onNewAntFsProgressUpdate(final AntFsState state, final long transferredBytes,
                                             final long totalBytes) {
            if ((Debug.ON) && (state != AntFsState.TRANSPORT_DOWNLOADING)) Log.v(TAG, "onNewAntFsProgressUpdate " + state.name());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch(state) {
                        //In Link state and requesting to link with the device in order to pass to Auth state
                        case LINK_REQUESTING_LINK:
                            antFsProgressDialog.setMax(4);
                            antFsProgressDialog.setProgress(1);
                            antFsProgressDialog.setMessage("[" + deviceName + "]\n" + getString(R.string.antFsProgressDialog_requesting_link));
                            break;

                        //In Authentication state, processing authentication commands
                        case AUTHENTICATION:
                            antFsProgressDialog.setMax(4);
                            antFsProgressDialog.setProgress(2);
                            antFsProgressDialog.setMessage("[" + deviceName + "]\n" + getString(R.string.antFsProgressDialog_authenticating));
                            break;

                        //In Authentication state, currently attempting to pair with the device
                        //Feedback given to the user here as pairing typically requires user interaction with the device
                        case AUTHENTICATION_REQUESTING_PAIRING:
                            antFsProgressDialog.setMax(4);
                            antFsProgressDialog.setProgress(2);
                            antFsProgressDialog.setMessage("[" + deviceName + "]\n" + getString(R.string.antFsProgressDialog_user_pairing_requested));

                            pairingAD = new AlertDialog.Builder(MainActivity.this).setMessage(getString(R.string.antFsProgressDialog_pairing_feedback))
                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                        }
                                    }).create();
                            pairingAD.show();
                            break;

                        //In Transport state, no requests are currently being processed
                        case TRANSPORT_IDLE:
                            if (pairingAD != null) pairingAD.cancel();
                            antFsProgressDialog.setMax(4);
                            antFsProgressDialog.setProgress(3);
                            antFsProgressDialog.setMessage("[" + deviceName + "]\n" + getString(R.string.antFsProgressDialog_requesting_download));
                            break;

                        //In Transport state, files are currently being downloaded
                        case TRANSPORT_DOWNLOADING:
                            antFsProgressDialog.setMessage("[" + deviceName + "]\n" + getString(R.string.antFsProgressDialog_downloading));
                            antFsProgressDialog.setMax(100);

                            if(transferredBytes >= 0 && totalBytes > 0) {
                                int progress = (int)(transferredBytes*100/totalBytes);
                                antFsProgressDialog.setProgress(progress);
                            }

                            break;
                        case UNRECOGNIZED:
                            if (Debug.ON) Log.v(TAG, "Failed: UNRECOGNIZED. PluginLib Upgrade Required?");
                            break;
                        default:
                            if (Debug.ON) Log.v(TAG, "Unknown ANT-FS State Code Received: " + state);
                            break;
                    }
                }
            });
        }
    }

    public static void showMessage(String msg, Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(msg)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                }).create();
        if (!activity.isFinishing()) builder.show();
    }

    public static void showGCpassMessage(final Context context)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.garmin_connect_invalid_password)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://connect.garmin.com/account")));
                        dialog.cancel();
                    }
                })
                .create().show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Debug.ON) Log.v(TAG, "onResume");
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Debug.ON) Log.v(TAG, "onActivityResult " + requestCode + "_" + resultCode + "__" + data);

        if (requestCode == SETTINGS_REQUEST) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            String backup_folder = prefs.getString("backup_folder", "");
            String gmail_to = prefs.getString("gmail_to", "");

            boolean isConfigured = false;
            if ((!backup_folder.equals(""))
                    || (!gmail_to.equals(""))) {
                isConfigured = true;
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("configured", isConfigured);
            if (gmail_to.length() > 0) editor.putString("gmail_to", gmail_to.trim().replaceAll("[\n\r]", ""));
            editor.apply();

            if (first_time) {
                if (isConfigured) PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putBoolean(FIRST_TIME, false).apply();
                resetPcc();
            }
        }
    }

    public static void deleteAllActivities() {
        if (mAdapter != null) mAdapter.deleteAllActivities();
    }

    public static void forgetDevices(Context c) {
        boolean found = false;
        Map<String, ?> allEntries = PreferenceManager.getDefaultSharedPreferences(c).getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
                Toast.makeText(c, entry.getKey(), Toast.LENGTH_LONG).show();
                PreferenceManager.getDefaultSharedPreferences(c).edit().remove(entry.getKey()).apply();
                found = true;
            }
        }
        if (found)
        {
            Intent mStartActivity = new Intent(c, MainActivity.class);
            int mPendingIntentId = 123456;
            PendingIntent mPendingIntent = PendingIntent.getActivity(c, mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager mgr = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            if (mgr == null) return;
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
            Thread thread = new Thread(){
                @Override
                public void run() {
                    try {
                        Thread.sleep(3500); // As I am using LENGTH_LONG in Toast
                    } catch (Exception ignored) { }
                    System.exit(0);
                }
            };
            thread.start();
        }
    }
}