package com.teosa.app.prototipo.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class DiscoveryClient {
    private DiscoveryClient() {}

    public static String discover(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress(DiscoveryAnnouncer.DISCOVERY_PORT));
            byte[] buffer = new byte[256];
            while (System.currentTimeMillis() < deadline) {
                socket.setSoTimeout((int) Math.min(1000, Math.max(1, deadline - System.currentTimeMillis())));
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try { socket.receive(packet); }
                catch (java.net.SocketTimeoutException ignored) { continue; }
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                if (message.startsWith("TEOSA_REPORT_SERVER|")) {
                    int port = Integer.parseInt(message.substring(message.indexOf('|') + 1));
                    return "http://" + packet.getAddress().getHostAddress() + ":" + port;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
