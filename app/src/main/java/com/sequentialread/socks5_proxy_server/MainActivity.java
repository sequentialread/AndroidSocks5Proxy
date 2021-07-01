package com.sequentialread.socks5_proxy_server;

// original code: https://github.com/edveen/AndroidSocks5Proxy

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.net.ServerSocket;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private String TAG = "socks5_proxy_server";
    public static ConnectivityManager connectivityManager;
    public static NetworkInfo networkInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "HELLO");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        networkInfo = connectivityManager.getActiveNetworkInfo();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(1080); //A port is randomly selected here
                    Log.d(TAG, "Port=" + serverSocket.getLocalPort());
                    while (true) {
                        Socket socket = serverSocket.accept();//If you can't get it, it will keep blocking
                        new Thread(new ServerThread(socket)).start();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }).start();
    }
}