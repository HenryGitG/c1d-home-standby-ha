package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class StandbyControlService extends Service {
    static final int PORT = 8765;
    private static final String ACTION_WAKE = "com.gathin.tvhome.action.WAKE";
    private static final String CHANNEL = "c1d_network_standby";
    private static final String PREFS = "standby-control";
    private static final String PREF_STANDBY = "standby";
    private static final int NOTIFICATION_ID = 8765;

    private final Object stateLock = new Object();
    private volatile boolean running;
    private volatile boolean standby;
    private ServerSocket server;
    private Thread serverThread;
    private PowerManager.WakeLock cpuLock;
    private WifiManager.WifiLock wifiLock;
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener nsdListener;
    private volatile boolean nsdRegistered;

    static void start(Context context) {
        startService(context, new Intent(context, StandbyControlService.class));
    }

    static void wake(Context context) {
        startService(context, new Intent(context, StandbyControlService.class).setAction(ACTION_WAKE));
    }

    private static void startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static boolean isStandby(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_STANDBY, false);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotification();
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        standby = preferences.getBoolean(PREF_STANDBY, false) && !StandbyController.isDisplayOn();
        preferences.edit().putBoolean(PREF_STANDBY, standby).apply();
        if (standby) acquireLocks();
        running = true;
        serverThread = new Thread(this::serve, "c1d-standby-http");
        serverThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_WAKE.equals(intent.getAction())) setStandby(false);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        unregisterDiscovery();
        releaseLocks();
        super.onDestroy();
    }

    private void createNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, getString(R.string.standby_service_name), NotificationManager.IMPORTANCE_MIN);
            channel.setDescription(getString(R.string.standby_service_description));
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.standby_service_name))
                .setContentText(getString(R.string.standby_service_description))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void serve() {
        try (ServerSocket listener = new ServerSocket()) {
            server = listener;
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(PORT), 8);
            registerDiscovery();
            while (running) {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(4000);
                    handle(socket);
                } catch (IOException ignored) {
                    if (!running) return;
                }
            }
        } catch (IOException ignored) {
        } finally {
            server = null;
        }
    }

    private void registerDiscovery() {
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName("C1D Projector");
        info.setServiceType("_c1d-standby._tcp.");
        info.setPort(PORT);
        info.setAttribute("id", Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID));
        info.setAttribute("path", "/api/power");
        info.setAttribute("version", "1");
        nsdListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                nsdRegistered = true;
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                nsdRegistered = false;
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                nsdRegistered = false;
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                nsdRegistered = false;
            }
        };
        nsdManager = (NsdManager) getSystemService(NSD_SERVICE);
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdListener);
    }

    private void unregisterDiscovery() {
        if (!nsdRegistered || nsdManager == null || nsdListener == null) return;
        try {
            nsdManager.unregisterService(nsdListener);
        } catch (IllegalArgumentException ignored) {
        }
        nsdRegistered = false;
    }

    private void handle(Socket socket) throws IOException {
        BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
        String requestLine = readLine(input);
        if (requestLine == null) return;
        String authorization = "";
        int contentLength = 0;
        String line;
        while ((line = readLine(input)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if ("authorization".equals(name)) authorization = value;
            else if ("content-length".equals(name)) {
                try {
                    contentLength = Math.min(1024, Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (!authorized(authorization)) {
            respond(socket, 401, "{\"error\":\"unauthorized\"}");
            return;
        }
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            respond(socket, 400, "{\"error\":\"bad request\"}");
            return;
        }
        String method = parts[0];
        String path = parts[1].split("\\?", 2)[0];
        byte[] bodyBytes = new byte[contentLength];
        int bodyLength = 0;
        while (bodyLength < contentLength) {
            int count = input.read(bodyBytes, bodyLength, contentLength - bodyLength);
            if (count < 0) break;
            bodyLength += count;
        }
        String body = new String(bodyBytes, 0, bodyLength, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if ("GET".equals(method) && "/api/power".equals(path)) {
            respond(socket, 200, stateJson());
        } else if ("POST".equals(method) && "/api/standby".equals(path)) {
            respondPower(socket, true);
        } else if ("POST".equals(method) && "/api/wake".equals(path)) {
            respondPower(socket, false);
        } else if ("POST".equals(method) && "/api/power".equals(path)
                && (body.contains("standby") || body.contains("off"))) {
            respondPower(socket, true);
        } else if ("POST".equals(method) && "/api/power".equals(path)
                && (body.contains("wake") || body.contains("on"))) {
            respondPower(socket, false);
        } else {
            respond(socket, 404, "{\"error\":\"not found\"}");
        }
    }

    private boolean authorized(String authorization) {
        byte[] provided = authorization.getBytes(StandardCharsets.UTF_8);
        byte[] expected = ("Bearer " + getString(R.string.standby_api_token)).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(provided, expected);
    }

    private void respondPower(Socket socket, boolean requestedStandby) throws IOException {
        if (!setStandby(requestedStandby)) {
            respond(socket, 500, "{\"error\":\"display control failed\"}");
            return;
        }
        respond(socket, 200, stateJson());
    }

    private boolean setStandby(boolean requestedStandby) {
        synchronized (stateLock) {
            if (requestedStandby) {
                acquireLocks();
                StandbyController.openHome(this);
                try {
                    Thread.sleep(250);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                if (!StandbyController.setDisplayPower(false)) {
                    releaseLocks();
                    return false;
                }
                standby = true;
            } else {
                if (!StandbyController.setDisplayPower(true)) return false;
                standby = false;
                StandbyController.openHome(this);
                releaseLocks();
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_STANDBY, standby).apply();
            return true;
        }
    }

    private void acquireLocks() {
        if (cpuLock == null) {
            PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
            cpuLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":network-standby");
            cpuLock.setReferenceCounted(false);
        }
        if (!cpuLock.isHeld()) cpuLock.acquire();
        if (wifiLock == null) {
            WifiManager manager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            wifiLock = manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, getPackageName() + ":network-standby");
            wifiLock.setReferenceCounted(false);
        }
        if (!wifiLock.isHeld()) wifiLock.acquire();
    }

    private void releaseLocks() {
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
    }

    private String stateJson() {
        return "{\"state\":\"" + (standby ? "standby" : "on") + "\"}";
    }

    private static String readLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        while (line.length() < 4096) {
            int value = input.read();
            if (value < 0) return line.length() == 0 ? null : line.toString();
            if (value == '\n') break;
            if (value != '\r') line.append((char) value);
        }
        return line.toString();
    }

    private static void respond(Socket socket, int status, String body) throws IOException {
        String reason = status == 200 ? "OK" : status == 401 ? "Unauthorized"
                : status == 400 ? "Bad Request" : status == 404 ? "Not Found" : "Internal Server Error";
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "Connection: close\r\n\r\n";
        BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(data);
        output.flush();
    }
}
