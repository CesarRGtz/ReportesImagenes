package com.teosa.app.prototipo.data;

import java.net.InetAddress;

public final class UserIdentity {
    private UserIdentity() {}

    public static String user() {
        return System.getProperty("user.name", "Usuario");
    }

    public static String computer() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception ex) { return System.getenv().getOrDefault("COMPUTERNAME", "Equipo"); }
    }

    public static String display() { return user() + " @ " + computer(); }
}
