package com.teosa.app.prototipo.data;

import java.nio.file.Path;

public final class AppDirectories {
    private AppDirectories() {}

    public static Path base() {
        String override = System.getProperty("teosa.data.dir");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath();
        return Path.of(System.getProperty("user.home"), "Documents", "TEOSA Reportes");
    }

    public static Path config() { return base().resolve("config.json"); }
    public static Path serverData() { return base().resolve("datos-servidor"); }
    public static Path cache() { return base().resolve("cache"); }
    public static Path pending() { return base().resolve("pendientes"); }
    public static Path backups() { return base().resolve("respaldos"); }
}
