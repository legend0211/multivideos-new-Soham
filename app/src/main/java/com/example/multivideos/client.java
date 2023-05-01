package com.example.multivideos;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class client extends  AppCompatActivity{
    private static final String SERVICE_TYPE = "_http._tcp.";
    private static final String SERVICE_NAME = "multivideos";
    private static final String TAG = "Client";
    private InputStream inputStream;
    private OutputStream outputStream;
    private NsdManager nsdManager;
    private NsdServiceInfo nsdServiceInfo;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private byte[] buffer = new byte[1024 * 1024];
    private  Context context;

    public client(Context context) {
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.context = context.getApplicationContext();
    }

    public void registerService(int port) {
        try {
            Log.d(TAG,"Entered registerService");
            serverSocket = new ServerSocket(port);
            nsdServiceInfo = new NsdServiceInfo();
            nsdServiceInfo.setServiceType(SERVICE_TYPE);
            nsdServiceInfo.setServiceName(SERVICE_NAME);
            nsdServiceInfo.setPort(port);

            nsdManager.registerService(nsdServiceInfo, NsdManager.PROTOCOL_DNS_SD, new NsdManager.RegistrationListener() {
                @Override
                public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                    Log.d(TAG, "Service registered: " + serviceInfo.getServiceName());
                    acceptConnection();
                }
                @Override
                public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.e(TAG, "Error code: " + errorCode);
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                    Log.d(TAG, "Service unregistered: " + serviceInfo.getServiceName());
                }
                @Override
                public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.e(TAG, "Error code: " + errorCode);
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }

   void  acceptConnection(){
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.execute(new Runnable() {
        @Override
        public void run() {
            try {
                Log.d(TAG, "Waiting for incoming connections...");
                clientSocket = serverSocket.accept();
                Log.d(TAG, "Sent video");
                // send the video file
                outputStream = clientSocket.getOutputStream();
                File f = new File(getnewPath());
                //inputStream = new FileInputStream(new File(getnewPath()));
                inputStream = new BufferedInputStream(new FileInputStream(f));
                int bytesRead,count=0;
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wifiManager.getConnectionInfo();
                int rssi = info.getRssi();

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    info = wifiManager.getConnectionInfo();
                    rssi += info.getRssi();
                    count++;
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
                System.out.println("RSSI client = "+(int)(rssi/(count+1)));
                outputStream.close();
                inputStream.close();
                clientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error: " + e.getMessage());
            } finally {
                close();
            }
        }
    });
}

    public void close() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }
        if (clientSocket != null) {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }

        if (nsdServiceInfo != null) {
            nsdManager.unregisterService(resolveListener);
        }

    }
    private NsdManager.RegistrationListener resolveListener = new NsdManager.RegistrationListener() {
        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "Service registered: " + serviceInfo.getServiceName());
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.e(TAG, "Error code: " + errorCode);
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "Service unregistered: " + serviceInfo.getServiceName());
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.e(TAG, "Error code: " + errorCode);
        }
    };

    String getnewPath(){
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/exoplayer.mp4";
        return path;
    }
}