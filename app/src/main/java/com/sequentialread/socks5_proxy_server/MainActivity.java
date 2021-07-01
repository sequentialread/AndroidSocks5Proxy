package com.sequentialread.socks5_proxy_server;

// original code: https://github.com/edveen/AndroidSocks5Proxy

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.format.Formatter;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private String TAG = "socks5_proxy_server";
    public static ConnectivityManager connectivityManager;
    public static NetworkInfo networkInfo;
    private OnScreenLog log;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        OnScreenLog.initialize(this);

        OnScreenLog.log(" __       __          .--.");
        OnScreenLog.log("(  \"\"--__(  \"\"-_    ,' .-.\\        \uD83C\uDF1F");
        OnScreenLog.log(" \"-_ __  \"\"--__ \"-_(  (^_^))      /");
        OnScreenLog.log("    (  \"\"\"--___\"\"--_)\" )-'(      /          ✨");
        OnScreenLog.log("     \"-__      \"\"---) -(   )__o-/,  ");
        OnScreenLog.log("         \"\"\"----___ / '   /--\"--'");
        OnScreenLog.log("                   (\"-_,`(    /");
        OnScreenLog.log("                    \\   \\ \\");
        OnScreenLog.log("                     `.  \\ | ");
        OnScreenLog.log("                       \\  \\/");
        OnScreenLog.log("       ✨              ||  \\");
        OnScreenLog.log("                     ,-'/`. \\  ✨");
        OnScreenLog.log("                     ) /   ) \\   ");
        OnScreenLog.log("                     |/    `-.\\");
        OnScreenLog.log("   \uD83C\uDF43                          `\\");
        OnScreenLog.log("                                               ");
        OnScreenLog.log("");
        OnScreenLog.log("Art credit: Ojo '98");
        OnScreenLog.log("  -- https://www.asciiart.eu/mythology/fairies");


        OnScreenLog.log("");
        OnScreenLog.log("ProxyServer starting up...");

        connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        networkInfo = connectivityManager.getActiveNetworkInfo();

        WifiManager wm = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ip = wm.getConnectionInfo().getIpAddress();
        List<String> ipAddresses = new ArrayList<>();
        if(ip == 0) {
            // if the ip address for the current wifi connection is zero, maybe it's wifi tethering mode?
            //
            ipAddresses = getNetworkInterfaceIpAddresses();
        } else {
            ipAddresses = new ArrayList<>();
            ipAddresses.add(String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), ip >> 24 & 0xff));
        }
        final List<String> finalIpAddresses = ipAddresses;

        // tell Android to please continue running our app at full speed even when the phone screen is off.
        // https://stackoverflow.com/questions/44862176/request-ignore-battery-optimizations-how-to-do-it-right
        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:"+getPackageName())));


        OnScreenLog.log("ignore battery optimizations (run in background) was requested.");

        new Thread(new Runnable() {
            @Override
            public void run() {
                ServerSocket serverSocket = null;
                while (true) {
                    try {
                        if(serverSocket == null || !serverSocket.isBound()) {
                            serverSocket = new ServerSocket(1080);
                        }
                        for(String ipString : finalIpAddresses) {
                            OnScreenLog.log("SOCKS5 Proxy Server is listening at " + ipString + ":" + serverSocket.getLocalPort());
                        }
                        while (true) {
                            Socket socket = serverSocket.accept();
                            new Thread(new ServerThread(socket)).start();
                        }
                    } catch (Exception e) {
                        OnScreenLog.log("Server Crashed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        e.printStackTrace();
                        if(serverSocket != null) {
                            try {
                                serverSocket.close();
                            } catch (IOException ioException) {}
                            serverSocket = null;
                        }
                    }
                    OnScreenLog.log("Attempting to restart in five seconds...");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {}
                }
            }
        }).start();
    }

    private List<String> getNetworkInterfaceIpAddresses() {
        List<String> toReturn = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        toReturn.add(inetAddress.getHostAddress());
                    }
                }
            }

        } catch (SocketException e) {
            OnScreenLog.log(
                    "Error occurred while attempting to detect your device's IP address: " + e.getMessage()
                    + ". The server should still work but I wont be able to tell you what IP to connect to."
            );
            e.printStackTrace();
        }
        return toReturn;
    }
}