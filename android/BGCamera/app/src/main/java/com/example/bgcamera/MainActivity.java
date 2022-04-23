package com.example.bgcamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {
    static final int CODE_PERM_SYSTEM_ALERT_WINDOW = 6111;
    Button butStart;
    Button butStop;
    Socket socket;
    static final int CODE_PERM_CAMERA = 6112;
    public final Context context=this;
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case CamService.ACTION_STOPPED:
                    flipButtonVisibility(false);
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //다른앱 위에 그리기 권한 체크 및 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "다른앱 위에 그리기 기능을 허용으로 바꿔주십시오", Toast.LENGTH_SHORT).show();
            // Don't have permission to draw over other apps yet - ask user to give permission
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(settingsIntent, CODE_PERM_SYSTEM_ALERT_WINDOW);
            return;
        }

        // 카메라 사용 권한 체크
        String permission= Manifest.permission.CAMERA;
        if(ContextCompat.checkSelfPermission(this,permission) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{permission},CODE_PERM_CAMERA);
        }

        //서버로 사진 보내기 위한 socket 연결
        try {
            socket= IO.socket("http://192.168.166.128:4000/");
            socket.connect();
            Log.d("Connectec","OK");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        socket.on("chat2",onconnect);

        initView();
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions,grantResults);
        switch (requestCode){
            case CODE_PERM_CAMERA:
                if(grantResults[0]!= PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(this,"퍼미션오류",Toast.LENGTH_LONG);
                    finish();
                }
        }
    }
    private void initView(){
        butStart = findViewById(R.id.butStart);
        butStop=findViewById(R.id.butStop);
        if(!Python.isStarted()){
            Python.start(new AndroidPlatform(this));
        }
        butStart.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(!isServiceRunning(context,CamService.class)){
                    notifyService(CamService.ACTION_START);
                    System.out.println("다음꺼실행");
                    finish();
                }
            }
        });
        butStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(context, CamService.class));
            }
        });
    }

    private Emitter.Listener onconnect = (args) -> {
        JSONObject receivedata = (JSONObject) args[0];
        Log.d("a",receivedata.toString());
    };

    private void notifyService(String action){
        Intent intent=new Intent(this,CamService.class);
        intent.setAction(action);
        startService(intent);
    }

    //버튼 교환
    private void flipButtonVisibility(boolean running){
        if(running){
            butStart.setVisibility(View.GONE);
        }else{
            butStart.setVisibility(View.VISIBLE);
        }
        if(running){
            butStop.setVisibility(View.VISIBLE);
        }else{
            butStop.setVisibility(View.GONE);
        }
    }

    //CamService가 실행중인지 체크
    public boolean isServiceRunning(Context context , Class<?> serviceClass){
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) return true;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //socket.emit("disconnect", null);
        //socket.disconnect();
        Log.d("aa","onDestroy");
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(receiver, new IntentFilter(CamService.ACTION_STOPPED));
        boolean running = isServiceRunning(this,CamService.class);
        flipButtonVisibility(running);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }
}