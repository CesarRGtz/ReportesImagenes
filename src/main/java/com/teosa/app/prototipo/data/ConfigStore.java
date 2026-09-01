package com.teosa.app.prototipo.data;

import java.io.IOException;
import java.nio.file.Files;

public final class ConfigStore {
    private ConfigStore() {}

    public static AppConfig load() {
        if (!Files.exists(AppDirectories.config())) return new AppConfig();
        try { return JsonSupport.read(AppDirectories.config(), AppConfig.class); }
        catch (Exception ex) { return new AppConfig(); }
    }

    public static void save(AppConfig config) throws IOException {
        JsonSupport.write(AppDirectories.config(), config);
    }
}
