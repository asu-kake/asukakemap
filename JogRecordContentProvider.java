package com.example.walkgame;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

import androidx.annotation.Nullable;

public class JogRecordContentProvider extends ContentProvider {
    private DatabaseHelper mDbHelper;

    private static final int JOGRECORD = 10;
    private static final int JOGRECORD_ID = 20;
    private static final String AUTHORITY = "com.example.walkgame.JogRecordContentProvider";

    private static final String BASE_PATH = "jogrecord";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH);

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        uriMatcher.addURI(AUTHORITY, BASE_PATH, JOGRECORD);
        uriMatcher.addURI(AUTHORITY, BASE_PATH + "/#", JOGRECORD_ID);
    }

    @Override
    public boolean onCreate(){
        mDbHelper = new DatabaseHelper(getContext());
        return false;
    }
    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder){
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();

        queryBuilder.setTables(DatabaseHelper.TABLE_JOGRECORD);

        int uriType = uriMatcher.match(uri);
        switch (uriType){
            case JOGRECORD :
                break;
            case JOGRECORD_ID :
                queryBuilder.appendWhere(DatabaseHelper.COLUMN_ID + "="
                                            +uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        Cursor cursor = queryBuilder.query(db, projection, selection,
                selectionArgs, null, null, sortOrder);

        return cursor;
    }
    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values){
        int uriType = uriMatcher.match(uri);
        SQLiteDatabase sqlDB = mDbHelper.getWritableDatabase();
        long id = 0;
        switch (uriType){
            case JOGRECORD :
                id = sqlDB.insert(DatabaseHelper.TABLE_JOGRECORD, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return Uri.withAppendedPath(uri, String.valueOf(id));
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs){
        return 0;
    }

    @Nullable
    @Override
    public String getType(Uri uri){
        return null;
    }
    @Nullable
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs){
        return 0;
    }
}
