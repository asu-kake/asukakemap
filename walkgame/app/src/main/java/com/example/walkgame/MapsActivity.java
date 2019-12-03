package com.example.walkgame;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import android.content.Loader;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import android.location.Address;
import android.Manifest;
import android.app.LoaderManager;
import android.content.ContentValues;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,GoogleApiClient.OnConnectionFailedListener,LocationListener,
        LoaderManager.LoaderCallbacks<Address> {

    private static final int MY_PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int MY_PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 2;
    private static final int ADDRESSLOADER_ID = 0;
    //INTERVAL:500 , FASTESTINTERVAL:16で綺麗な線が描けた
    private static final int INTERVAL = 500;
    private static final int FASTESTINTERVAL = 16;

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private static final LocationRequest REQUEST = LocationRequest.create()
            .setInterval(INTERVAL) //位置情報の更新間隔をmsで設定
            .setFastestInterval(FASTESTINTERVAL) //最速の位置情報の更新間隔
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); //位置情報取得要求の優先順位
    private FusedLocationProviderApi mFusedLocationProviderApi = LocationServices.FusedLocationApi;
    private List<LatLng> mRunList = new ArrayList<LatLng>();
    private WifiManager mWifi;
    private boolean mWifiOff = false;
    private long mStartTimeMills;
    private double mMeter = 0.0; //メートル
    private double mElapsedTime = 0.0; //ミリ秒
    private double mSpeed = 0.0;
    private DatabaseHelper mDbHelper;
    private boolean mStart = false;
    private boolean mFirst = false;
    private boolean mStop = false;
    private boolean mAsked = false;
    private Chronometer mChronometer;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //メンバー変数が初期化されることへの対処
        outState.putBoolean("ASKED", mAsked);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        //画面をスリープにしない
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mDbHelper = new DatabaseHelper(this);

        ToggleButton tb = (ToggleButton) findViewById(R.id.toggleButton);
        tb.setChecked(false); //OFFへ変更

        //ToggleのCheckが変更したタイミングで呼び出されるリスナー
        tb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //トグルキーが変更された際に呼び出される
                if (isChecked) {
                    startChronometer();
                    mStart = true;
                    mFirst = true;
                    mStop = false;
                    mMeter = 0.0;
                    mRunList.clear();
                } else {
                    stopChronometer();
                    mStop = true;
                    calcSpeed();
                    saveConfirm();
                    mStart = false;
                }
            }
        });

    }


    private void startChronometer() {
        mChronometer = (Chronometer) findViewById(R.id.chronometer);
        //電源がON時からの経過時間の値をベースに
        mChronometer.setBase(SystemClock.elapsedRealtime());
        mChronometer.start();
        mStartTimeMills = System.currentTimeMillis();
    }

    private void stopChronometer() {
        mChronometer.stop();
        //ミリ秒
        mElapsedTime = SystemClock.elapsedRealtime() - mChronometer.getBase();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mAsked) {
            //Log.v("exec mAsked","" + mAsked);
            wifiConfirm();
            mAsked = !mAsked;
        }

        mGoogleApiClient.connect();

        if(mMap != null){
            mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            //屋内マップ表示を無効にする
            mMap.setIndoorEnabled(false);
            //現在地表示ボタンを有効にする
            mMap.setMyLocationEnabled(true);
            //地図の種類
            mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);

        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        //現在位置アイコンを表示する
        mMap.setMyLocationEnabled(true);

        //マップをロングタップした時の処理
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                Intent intent = new Intent(MapsActivity.this, JogView.class);
                startActivity(intent);
            }
        });

        //DangerousなPermissionはリクエストして許可をもらわないと使えない
        //位置情報許可確認
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                //一度拒否された時、Rationale(理論的根拠)を説明して、再度許可ダイアログを出すようにする
                new AlertDialog.Builder(this)
                        .setTitle("許可が必要です")
                        .setMessage("移動に合わせて地図を動かすためには、ACCESS_FINE_LOCATIONを許可してください")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //OK button pressed
                                requestAccessFineLocation();
                            }
                        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showToast("GPS機能が使えないので、地図は動きません");
                    }
                }).show();
            } else {
                //まだ許可を求める前の時、許可を求めるダイアログを表示します。
                requestAccessFineLocation();
            }
        }
    }

    private void requestAccessFineLocation() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                MY_PERMISSION_REQUEST_ACCESS_FINE_LOCATION);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],int[] grantResults){
        switch(requestCode){
            case MY_PERMISSION_REQUEST_ACCESS_FINE_LOCATION:{
                //ユーザが許可した時
                //許可が必要な機能を改めて実行する
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    //
                }else{
                    //ユーザが許可しなかった時
                    //許可されなかったため機能が実行できないことを表示する
                    showToast("GPS機能が使えないので、地図は動きません");
                    //以下は java.lang.RuntimeException になる
                    //mMap.setMyLocationEnabled(true);
                }
                return;
            }
            case MY_PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE:{
                //ユーザが許可した時
                //許可が必要な機能を改めて実行する
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    saveConfirmDialog();
                }else{
                    //ユーザが許可しなかった時
                    //許可されなかったため機能が実行できないことを表示する
                    showToast("外部へのファイルの保存がされなかったので、記録でいません");
                }
                return;
            }
        }
    }

    private void wifiConfirm(){
        mWifi = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);

        if(mWifi.isWifiEnabled()){
            wifiConfirmDialog();
        }
    }

    private void wifiConfirmDialog(){
        DialogFragment newFragment = WifiConfirmDialogFragment.newInstance(
                R.string.wifi_confirm_dialog_title, R.string.wifi_confirm_dialog_massage);

        newFragment.show(getSupportFragmentManager(), "dialog");
    }

    public void wifiOff(){
        mWifi.setWifiEnabled(false);
        mWifiOff = true;
    }
    @Override
    public void onConnected(@Nullable Bundle bundle){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED){
            return;
        }
        mFusedLocationProviderApi.requestLocationUpdates(mGoogleApiClient, REQUEST, this);
    }
    @Override
    public void onLocationChanged(Location location){
        //stop後は動かさない
        if(mStop){
            return;
        }
        CameraPosition cameraPos = new CameraPosition.Builder()
                .target(new LatLng(location.getLatitude(),location.getLongitude())).zoom(19)
                .bearing(0).build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPos));


        final double bomlat = location.getLatitude();
        final double bomlon = location.getLongitude();
        final LatLng bomlatlng = new LatLng(bomlat,bomlon);
        final MarkerOptions options = new MarkerOptions();

        final double lat = location.getLatitude();
        final double lon = location.getLongitude();
        LatLng mylatlng = new LatLng(lat,lon);

        Button removebutton = (Button) findViewById(R.id.removebutton);
        //latがbomlatの近く、かつ、lonがbomlonの近くにきた時に解除ボタンを押した時の処理
        removebutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(bomlat - lat <= 0.0001 || lat - bomlat <= 0.0001 && bomlon - lon <= 0.0001 || lon - bomlon <= 0.0001){
                    //設置されたマーカー（爆弾）を削除する(地図を再描画）
                    mMap.clear();
                    showToast("爆弾を解除しました");
                }else{
                    showToast("この距離では爆弾を解除できません");
                }
            }
        });

        Button button = (Button) findViewById(R.id.button);
        //爆弾設置ボタンが押された時の処理
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //設置完了の通知
                showToast("爆弾を設置しました！");
                //サーバーに現在位置情報を送信
                //現在位置にマーカーを追加
                //マーカー設定
                options.position(bomlatlng);
                //ランチャーアイコン
                BitmapDescriptor icon = BitmapDescriptorFactory.fromResource(R.mipmap.ic_launcher);
                options.icon(icon);
                mMap.addMarker(options);

                //爆弾を設置した位置の緯度経度？
                //showToast("bomlat" + bomlat + "bomlon" + bomlon);
            }
        });

        if(mStart){
            if(mFirst){
                Bundle args = new Bundle();
                args.putDouble("lat",location.getLatitude());
                args.putDouble("lon",location.getLongitude());

                mFirst = !mFirst;
            }else{
                //移動線を描画
                drawTrace(mylatlng);
                //走行距離を累積
                sumDistance();
            }
        }

    }

    private void drawTrace(LatLng latlng){
        mRunList.add(latlng);
        if(mRunList.size() > 2){
            PolylineOptions polyOptions = new PolylineOptions();
            for(LatLng polyLatLng : mRunList){
                polyOptions.add(polyLatLng);
            }
            polyOptions.color(Color.BLUE);
            polyOptions.width(3);
            polyOptions.geodesic(false);
            mMap.addPolyline(polyOptions);
        }
    }

    private void sumDistance(){
        if(mRunList.size() < 2){
            return;
        }
        mMeter = 0;
        float[] results = new float[3];
        int i = 1;
        while(i < mRunList.size()){
            results[0] = 0;
            Location.distanceBetween(mRunList.get(i-1).latitude, mRunList.get(i-1).longitude,
                    mRunList.get(i).latitude, mRunList.get(i).longitude, results);
            mMeter += results[0];
            i++;
        }
        //distanceBetweenの距離はメートル単位
        double disMeter = mMeter / 1000;
        TextView disText = (TextView)findViewById(R.id.disText);
        disText.setText(String.format("%.2f" + " km", disMeter));
    }

    private void calcSpeed(){
        sumDistance();
        mSpeed = (mMeter/1000) / (mElapsedTime/1000) * 60 * 60;
    }
    private void saveConfirm(){
        //DangerousなPermissionはリクエストして許可してもらわないと使えない
        //ウォーキングの記録
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                //一度拒否された時、Rationale(理論的根拠)を説明して、再度許可ダイアログを出すようにする
                new AlertDialog.Builder(this)
                        .setTitle("許可が必要です")
                        .setMessage("ウォーキングの記録を保存するためには、WRITE_EXTERNAL_STORAGEを許可してください")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //OK button pressed
                                requestWriteExternalStorage();
                            }
                        }).show();
            }else{
                //まだ許可を求める前の時、許可を求めるダイアログを表示します
                requestWriteExternalStorage();
            }
        }else{
            saveConfirmDialog();
        }
    }

    private void requestWriteExternalStorage(){
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                MY_PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
    }

    private void saveConfirmDialog(){
        String message = "時間:";
        TextView disText = (TextView)findViewById(R.id.disText);

        message = message + mChronometer.getText().toString() + " " +
                "距離:" + disText.getText() + " " +
                "時速:" + String.format("%.2f" + " km", mSpeed);

        DialogFragment newFragment = SaveConfirmDialogFragment.newInstance(
                R.string.save_confirm_dialog_title, message);

        newFragment.show(getSupportFragmentManager(), "dialog");
    }

    @Override
    protected void onPause(){
        super.onPause();
        if(mGoogleApiClient.isConnected()){
            stopLocationUpdates();
        }
        mGoogleApiClient.disconnect();
    }
    @Override
    protected void onStop(){
        super.onStop();
        //自プログラムがオフにした場合はWIFIをオンにする処理
        if(mWifiOff){
            mWifi.setWifiEnabled(true);
        }
    }
    protected void stopLocationUpdates(){
        mFusedLocationProviderApi.removeLocationUpdates(mGoogleApiClient,this);
    }
    @Override
    public void onConnectionSuspended(int cause){
        //Do nothing
    }
    @Override
    public void onConnectionFailed(ConnectionResult result){
        //Do nothing
    }

    @Override
    public Loader<Address> onCreateLoader(int id, Bundle args){
        double lat = args.getDouble("lat");
        double lon = args.getDouble("lon");
        return null;
    }


    @Override
    public void onLoadFinished(Loader<Address> loader, Address result){
        if(result != null){
            StringBuilder sb = new StringBuilder();
            for(int i = 1; i<result.getMaxAddressLineIndex() + 1; i++){
                String item = result.getAddressLine(i);
                if(item == null){
                    break;
                }
                sb.append(item);
            }
            TextView address = (TextView)findViewById(R.id.address);

            address.setText(sb.toString());
        }
    }

    @Override
    public void onLoaderReset(Loader<Address> loader){

    }

    public void saveJogViaCTP(){

        String strDate = new SimpleDateFormat("yyyy/MM/dd").format(mStartTimeMills);
        TextView txtAddress = (TextView)findViewById(R.id.address);

        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_DATE, strDate);
        values.put(DatabaseHelper.COLUMN_ELAPSEDTIME, mChronometer.getText().toString());
        values.put(DatabaseHelper.COLUMN_DISTANCE, mMeter);
        values.put(DatabaseHelper.COLUMN_SPEED, mSpeed);
        values.put(DatabaseHelper.COLUMN_ADDRESS, txtAddress.getText().toString());
        Uri uri = getContentResolver().insert(JogRecordContentProvider.CONTENT_URI, values);
        showToast("データを保存しました");
    }

    private void showToast(String msg){
        Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        error.show();
    }
}




