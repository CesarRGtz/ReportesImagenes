package com.teosa.app.prototipo.data;

public class AppConfig {
    public enum Role { PRIMARY, SECONDARY }

    private Role role;
    private String serverUrl = "";
    private int serverPort = 43821;

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getServerUrl() { return serverUrl == null ? "" : serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    public int getServerPort() { return serverPort <= 0 ? 43821 : serverPort; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }
}
