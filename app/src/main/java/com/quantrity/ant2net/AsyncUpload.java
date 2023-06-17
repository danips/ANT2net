package com.quantrity.ant2net;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class AsyncUpload extends AsyncTask<String, Integer, Boolean> {
    private static final String TAG = "AsyncUpload";
    private final ActivityAdapter mAdapter;
    private final List<SportActivity> activitiesList;
    private final Activity mContext;

    enum ServicesEnum {
        ALL,
        EMAIL
    }

    private final ServicesEnum service;

    AsyncUpload(Activity context, ActivityAdapter adapter, List<SportActivity> activitiesList) {
        this.mContext = context;
        this.mAdapter = adapter;
        this.activitiesList = activitiesList;
        this.service = ServicesEnum.ALL;
    }

    AsyncUpload(Activity context, ActivityAdapter adapter, SportActivity activity, ServicesEnum service) {
        this.mContext = context;
        this.mAdapter = adapter;
        this.activitiesList = new ArrayList<>();
        this.activitiesList.add(activity);
        this.service = service;
    }

    @Override
    protected Boolean doInBackground(String... paths) {
        //Make the buttons spin
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String emailTo = prefs.getString("gmail_to", "");

        if (service.equals(ServicesEnum.ALL) || service.equals(ServicesEnum.EMAIL)) {
            if (!emailTo.isEmpty()) {
                for (SportActivity sa : activitiesList) {
                    updateStatus((byte) 2, sa, ServicesEnum.EMAIL);
                }
            }
        }

        mContext.runOnUiThread(mAdapter::notifyDataSetChanged);

        try {
            File outputFile = new File(mContext.getExternalFilesDir(null), "fit.zip");
            
            if (!outputFile.exists() || outputFile.isDirectory()) {
                return false;
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile.getAbsolutePath());
                 ZipOutputStream out = new ZipOutputStream(fos)) {
                out.setLevel(Deflater.BEST_COMPRESSION);
                for (SportActivity entry : activitiesList) {
                    ZipEntry zipEntry = new ZipEntry(entry.getDateString() + ".fit");
                    out.putNextEntry(zipEntry);
                    out.write(entry.getRawBytes(mContext));
                    out.closeEntry();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("vnd.android.cursor.dir/email");
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailTo});

            Uri uri = FileProvider.getUriForFile(mContext, mContext.getApplicationContext().getPackageName() + ".provider", outputFile);
            emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Subject");

            mContext.startActivity(Intent.createChooser(emailIntent, "Send email..."));

            for (SportActivity sa : activitiesList) {
                updateStatusAll((byte) 1, sa);
            }
        } catch (Exception e) {
            e.printStackTrace();
            for (SportActivity sa : activitiesList) {
                updateStatusAll((byte) 0, sa);
            }
            mContext.runOnUiThread(mAdapter::notifyDataSetChanged);
        }
        return true;
    }

    private void updateStatusAll(byte status, final SportActivity sa) {
        synchronized (sa) {
            java.io.File file = new java.io.File(mContext.getFilesDir() + "/fits", sa.toString());
            if (service.equals(ServicesEnum.ALL) || service.equals(ServicesEnum.EMAIL))
                sa.setEm(status);
            java.io.File file2 = new java.io.File(mContext.getFilesDir() + "/fits", sa.toString());
            file.renameTo(file2);
        }
    }

    private void updateStatus(byte status, final SportActivity sa, ServicesEnum se) {
        synchronized (sa) {
            java.io.File file = new java.io.File(mContext.getFilesDir() + "/fits", sa.toString());
            switch (se) {
                case EMAIL:
                    sa.setEm(status);
                    break;
            }
            java.io.File file2 = new java.io.File(mContext.getFilesDir() + "/fits", sa.toString());
            file.renameTo(file2);
        }
    }
}
