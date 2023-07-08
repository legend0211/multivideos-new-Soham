package com.example.multivideos;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class server extends AppCompatActivity{
    private static final String SERVICE_TYPE = "_http._tcp.";
    private static final String TAG = "Server";
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private NsdManager.ResolveListener resolveListener;
    private Socket socket;
    private String videoName;
    long startTime, endTime;
    private Context context = this;

    public server(Context context,String videoName) {
        this.context = context.getApplicationContext();
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.videoName=videoName;
    }

    public void discoverServices() {
        Log.d(TAG, "Discovery services");
        startTime = System.currentTimeMillis();
        //startTime = TimeUnit.MILLISECONDS.toSeconds(startTime);
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service found: " + serviceInfo.getServiceName());
                //nsdManager.resolveService(serviceInfo, resolveListener);
                resolveService(serviceInfo);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service lost: " + serviceInfo.getServiceName());
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Error code (onStartDiscoveryFailed) : " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Error code (onStopDiscoveryFailed) : " + errorCode);
            }
        };

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    public void stopDiscovery() {
        nsdManager.stopServiceDiscovery(discoveryListener);
    }

    public void resolveService(NsdServiceInfo serviceInfo) {
        resolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Error code (onResolveFailed) : " + errorCode);
            }
            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                stopDiscovery();
                saveVideo(serviceInfo);
            }
        };

        nsdManager.resolveService(serviceInfo, resolveListener);
    }

    public void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket: " + e.getMessage());
            }
        }
        if (resolveListener != null) {
            nsdManager.stopServiceDiscovery(discoveryListener);
            //nsdManager.unregisterService(resolveListener);
            resolveListener = null;
        }
        if (discoveryListener != null) {
            nsdManager.stopServiceDiscovery(discoveryListener);
            discoveryListener = null;
        }
    }

    public void saveVideo(NsdServiceInfo serviceInfo) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Waiting for incoming connections...");
                    InetAddress host = InetAddress.getByName(serviceInfo.getHost().getHostAddress());
                    int port = serviceInfo.getPort();

                    // connect to the server
                    socket = new Socket(host, port);
                    endTime = System.currentTimeMillis();
                    // receive the video
                    InputStream inputStream = socket.getInputStream();
                    File outputFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), videoName);
                    if(outputFile.exists()){
                        outputFile.delete();
                        outputFile.createNewFile();
                    }

                    OutputStream outputStream = new FileOutputStream(outputFile);
                    byte[] buffer = new byte[1024 * 1024];
                    int bytesRead, count=0;
                    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    WifiInfo info = wifiManager.getConnectionInfo();
                    int rssi = info.getRssi();

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        //Log.d(TAG, "Bytes read = "+(++count));
                        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                        info = wifiManager.getConnectionInfo();
                        rssi += info.getRssi();
                        count++;
                        if(count == 2){
                            Player.storage_exists = 1;
                        }
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    inputStream.close();
                    outputStream.close();
                    socket.close();
                    String text = "Received video to storage from "+host;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_LONG).show();
                        }
                    });
                    Player.storage_exists = 1;
                    System.out.println("RSSI server = "+(int)(rssi/(count+1)));
                    Log.d(TAG, "Search time = "+(endTime-startTime));
                    endTime = System.currentTimeMillis();
                    //endTime = TimeUnit.MILLISECONDS.toSeconds(endTime);
                    Log.d(TAG, "Total time = "+(endTime-startTime));
                    Log.d(TAG, "Video received from "+host+" : " + outputFile.getAbsolutePath());
                } catch (IOException e) {
                    Log.e(TAG, "Error: " + e.getMessage());
                }
            }
        });
    }
}