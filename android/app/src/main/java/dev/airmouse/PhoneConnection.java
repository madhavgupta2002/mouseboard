package dev.airmouse;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One bounded, ordered TCP stream; callbacks always run on the UI thread. */
final class PhoneConnection {
    interface Listener { void changed(String text, boolean connected, boolean enabled); }
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayBlockingQueue<String> clicks = new ArrayBlockingQueue<>(16);
    private volatile long generation;
    private volatile boolean connected, macEnabled;
    private boolean active = true;
    private Socket socket;
    private double pendingX, pendingY;

    PhoneConnection(Listener listener) { this.listener = listener; }

    void start(String host, int port, String code) {
        stop();
        final long id = generation;
        publish(id, "Connecting…", false, false);
        new Thread(() -> run(id, host, port, code), "airmouse-connection").start();
    }

    synchronized void stop() {
        generation++;
        connected = false;
        macEnabled = false;
        pendingX = pendingY = 0;
        clicks.clear();
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
            socket = null;
        }
    }

    synchronized void setActive(boolean value) {
        active = value;
        clearMotion();
        clicks.clear();
    }
    synchronized void clearMotion() { pendingX = pendingY = 0; }

    synchronized void move(double x, double y) {
        if (!connected || !macEnabled || !active) return;
        pendingX = Math.max(-200, Math.min(200, pendingX + x));
        pendingY = Math.max(-200, Math.min(200, pendingY + y));
    }

    synchronized void click(String button) {
        if (connected && macEnabled && active) clicks.offer("{\"type\":\"click\",\"button\":\"" + button + "\"}");
    }

    private void run(long id, String host, int port, String code) {
        Socket connection = new Socket();
        ScheduledExecutorService sender = null;
        String finalMessage = "Disconnected";
        try {
            synchronized (this) {
                if (id != generation) return;
                socket = connection;
            }
            connection.connect(new InetSocketAddress(host, port), 4000);
            connection.setTcpNoDelay(true);
            connection.setSoTimeout(4500);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            JSONObject hello = new JSONObject();
            hello.put("type", "hello").put("version", 1).put("code", code);
            writer.write(hello.toString()); writer.newLine(); writer.flush();
            JSONObject welcome = new JSONObject(readLine(reader));
            if (!"welcome".equals(welcome.optString("type"))) {
                throw new IOException(welcome.optString("message", "Mac refused the connection."));
            }
            if (welcome.optInt("version") != 1) throw new IOException("This Mac app uses a different protocol version.");
            synchronized (this) {
                if (id != generation) return;
                connected = true;
                macEnabled = welcome.optBoolean("enabled");
                pendingX = pendingY = 0;
            }
            publish(id, macEnabled ? "Connected · ready to point" : "Connected · enable control on Mac", true, macEnabled);
            sender = Executors.newSingleThreadScheduledExecutor();
            final long[] heartbeat = {0};
            sender.scheduleAtFixedRate(() -> {
                if (id != generation) return;
                try {
                    StringBuilder batch = new StringBuilder();
                    synchronized (PhoneConnection.this) {
                        if (connected && macEnabled && active && (pendingX != 0 || pendingY != 0)) {
                            batch.append("{\"type\":\"move\",\"dx\":").append(pendingX)
                                 .append(",\"dy\":").append(pendingY).append("}\n");
                        }
                        pendingX = pendingY = 0;
                        String click;
                        while ((click = clicks.poll()) != null) batch.append(click).append('\n');
                    }
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (now - heartbeat[0] >= 1000) {
                        batch.append("{\"type\":\"heartbeat\"}\n");
                        heartbeat[0] = now;
                    }
                    if (batch.length() > 0) { writer.write(batch.toString()); writer.flush(); }
                } catch (Exception failure) {
                    try { connection.close(); } catch (IOException ignored) { }
                }
            }, 0, 16, TimeUnit.MILLISECONDS);
            while (id == generation) {
                JSONObject message = new JSONObject(readLine(reader));
                if ("state".equals(message.optString("type"))) {
                    synchronized (this) {
                        macEnabled = message.optBoolean("enabled");
                        pendingX = pendingY = 0;
                        clicks.clear();
                    }
                    publish(id, macEnabled ? "Connected · ready to point" : "Connected · paused on Mac", true, macEnabled);
                } else if ("error".equals(message.optString("type"))) {
                    throw new IOException(message.optString("message", "Mac disconnected."));
                }
            }
        } catch (Exception error) {
            String detail = error.getMessage();
            finalMessage = detail == null || detail.isEmpty() ? "Connection lost. Check Wi-Fi and reconnect." : detail;
        } finally {
            if (sender != null) sender.shutdownNow();
            try { connection.close(); } catch (IOException ignored) { }
            synchronized (this) {
                if (id == generation) {
                    connected = false;
                    macEnabled = false;
                    pendingX = pendingY = 0;
                    socket = null;
                    clicks.clear();
                }
            }
            publish(id, finalMessage, false, false);
        }
    }

    private String readLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        while (line.length() <= 4096) {
            int character = reader.read();
            if (character == -1) throw new IOException("Mac disconnected. Tap Connect to try again.");
            if (character == '\n') return line.toString();
            if (character != '\r') line.append((char) character);
        }
        throw new IOException("Invalid response from Mac.");
    }

    private void publish(long id, String text, boolean paired, boolean enabled) {
        main.post(() -> { if (id == generation) listener.changed(text, paired, enabled); });
    }
}
