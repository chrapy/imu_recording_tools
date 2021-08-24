package com.pascaldornfeld.gsdble.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class MyDatabaseHelper extends SQLiteOpenHelper {

    private Context context;
    private static final String DATABASE_NAME = "DevicesLibrary.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_NAME = "myDevices";
    private static final String COLUMN_MAC = "deviceMac";
    private static final String COLUMN_NAME = "deviceName";
    private static final String COLUMN_DRIFT = "deviceDriftFactor";

    public MyDatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String query = "CREATE TABLE " + TABLE_NAME +
                " (" + COLUMN_MAC + " TEXT PRIMARY KEY, " +
                COLUMN_NAME + " TEXT, " +
                COLUMN_DRIFT + " TEXT);";
        db.execSQL(query);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void addDevice(String deviceMac, String deviceName, String deviceDrift){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_MAC, deviceMac);
        values.put(COLUMN_NAME, deviceName);
        values.put(COLUMN_DRIFT, deviceDrift);

        long result = db.insert(TABLE_NAME, null, values);
        if (result == -1){
            Toast.makeText(context, "Adding the Device failed!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Added new Sensor to known devices", Toast.LENGTH_SHORT).show();
        }
    }

    public boolean checkDevice(String deviceMac){
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {deviceMac};
        boolean exists;

        Cursor cursor = db.rawQuery("SELECT * FROM "+ TABLE_NAME + " WHERE " + COLUMN_MAC + "=?", selectionArgs);

        if (cursor.getCount()<=0){
            exists = false;
        } else{
            exists = true;
        }

        cursor.close();

        return exists;
    }

    public String getDeviceName(String deviceMac){
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {deviceMac};
        String name = deviceMac;

        Cursor cursor = db.rawQuery("SELECT "+ COLUMN_NAME + " FROM "+ TABLE_NAME + " WHERE " + COLUMN_MAC + "=?", selectionArgs);

        cursor.moveToFirst();
        name = cursor.getString(0);

        Log.e("Name: ", name);
        cursor.close();
        //Toast.makeText(context, name, Toast.LENGTH_SHORT).show();

        return name;
    }
}
