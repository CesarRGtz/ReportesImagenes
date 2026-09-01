package com.teosa.app.prototipo.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class DiscoveryAnnouncer implements AutoCloseable {
    public static final int DISCOVERY_PORT = 43822;
    private volatile boolean running;
    private Thread thread;
    private final int httpPort;

    public DiscoveryAnnouncer(int httpPort) { this.httpPort = httpPort; }

    public void start() {
        running = true;
        thread = Thread.ofVirtual().name("teosa-discovery-announcer").start(() -> {
            byte[] data = ("TEOSA_REPORT_SERVER|" + httpPort).getBytes(StandardCharsets.UTF_8);
            while (running) {
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setBroadcast(true);
                    socket.send(new DatagramPacket(data, data.length,
                            InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT));
                } catch (Exception ignored) {}
                try { Thread.sleep(2000); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
            }
        });
    }

    @Override public void close() {
        running = false;
        if (thread != null) thread.interrupt();
    }
}
