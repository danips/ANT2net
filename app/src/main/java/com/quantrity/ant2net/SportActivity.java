package com.quantrity.ant2net;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SportActivity {
    private final static String TAG = "SportActivity";
    private short sport;
    private long date;
    private String dateString;
    private float distance;
    private float duration;
    private float trainingEffect = -1f;
    //0 -> Failed to upload
    //1 -> Successful upload
    //2 -> in progess
    //3 -> not defined

    private int em = 3;
    private byte[] rawBytes;

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US);

    public SportActivity(short sport, long date, float distance, float duration, float trainingEffect) {
        this.sport = sport;
        this.date = date;
        this.dateString = sdf.format(new Date(date));
        this.distance = distance;
        this.duration = duration;
        if (trainingEffect != 0) this.trainingEffect = trainingEffect;
    }

    public SportActivity(short sport, long date, float distance, float duration, int gc, int st, int tp, int spt, int gd, int em, int dr, float trainingEffect) {
        this(sport, date, distance, duration, trainingEffect);
        this.em = em;
    }

    void resetUploadStatus() {
        this.em = 3;
    }

    public short getSport() {
        return sport;
    }

    public void setSport(short sport) {
        this.sport = sport;
    }

    long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        this.duration = duration;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    int getEm() {
        return em;
    }

    void setEm(int em) {
        this.em = em;
    }

    byte[] getRawBytes() { return rawBytes; }

    byte[] getRawBytes(Context context) {
        if ((rawBytes == null) && (context != null)) {
            try {
                rawBytes = readFile(context.getFilesDir() + "/fits/" + toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return rawBytes;
    }

    void setRawBytes(byte[] rawBytes) {
        this.rawBytes = rawBytes;
    }

    String getDateString() {
        return dateString;
    }

    float getTrainingEffect() {
        return trainingEffect;
    }

    @Override
    public String toString() {
        return date + "_" + sport + "_" + String.format("%.0f", distance * 1000) + "_" + String.format("%.0f", duration * 1000)
                + ((em == 3) ? "" : "_em" + em) + ((trainingEffect == -1f) ? "" : "_te" + String.format("%.0f", trainingEffect * 10)) ;
    }

    static byte[] readFile(String file) throws IOException {
        // Open file
        RandomAccessFile f = new RandomAccessFile(new File(file), "r");
        try {
            // Get and check length
            long long_length = f.length();
            int length = (int) long_length;
            if (length != long_length) throw new IOException("File size >= 2 GB");
            // Read file and return data
            byte[] data = new byte[length];
            f.readFully(data);
            return data;
        } finally {
            f.close();
        }
    }

    static boolean delete(SportActivity sa, Context context) {
        File file = new File(context.getFilesDir() + "/fits/" + sa.toString());
        return file.delete();
    }
}
