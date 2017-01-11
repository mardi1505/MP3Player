package com.droidsdoit.mp3player;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

/**
 * Created by Paul Keeling on 1/7/2017.
 */

public class MP3DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_FILE = "mp3URLs.db";
    private static String TAG = "MP3DatabaseHelper";

    private static final int DB_VERSION = 1;
    private static final String TABLE = "mp3urls";
    private static final String DB_CREATE_SQL = "CREATE TABLE " + TABLE + " (_id integer, url TEXT, name TEXT, primary key(_id));";
    private static final String INSERT = "INSERT INTO " + TABLE + " (url,name) values(?,?)";

    private SQLiteDatabase mDb;
    private SQLiteStatement insertStmt;

    public MP3DatabaseHelper(Context context) {
        super(context, DB_FILE, null, DB_VERSION);

        mDb = this.getWritableDatabase();
        insertStmt = mDb.compileStatement(INSERT);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            db.execSQL(DB_CREATE_SQL);
        } catch(Exception ex) {
            Log.e(TAG, "onCreate exception: " + ex.getMessage());
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("drop table if exists " + TABLE);
        onCreate(db);
    }

    /*
     * Open read only database.
     */
    private Boolean open() throws SQLiteException {
        try {
            if (mDb == null)
                mDb = getReadableDatabase();
        }
        catch(SQLiteException ex) {
            Log.e(TAG, "Open database exception: " + ex.getMessage());
            return false;
        }
        return true;
    }

    public void insert(String url, String friendlyName) {
        insertStmt.bindString(1, url);
        insertStmt.bindString(2, friendlyName);
        try {
            insertStmt.execute();
        } catch (android.database.sqlite.SQLiteConstraintException e) {
            Log.e(TAG, "insert exception: " + e.getMessage());
        }
    }

    public Cursor queryMP3s() {
        try {
            Cursor cursor = mDb.query(TABLE, new String[] {"_id", "url", "name"}, null, null, null, null, null);
            return cursor;
        } catch(Exception ex) {
            Log.i(TAG, "queryURLs exception: " + ex.getLocalizedMessage());
        }
        return null;
    }
}
