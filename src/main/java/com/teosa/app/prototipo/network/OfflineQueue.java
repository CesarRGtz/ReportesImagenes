package com.teosa.app.prototipo.network;

import com.teosa.app.prototipo.data.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

public class OfflineQueue {
    public void enqueue(ReportTransfer transfer) throws IOException {
        Path file = AppDirectories.pending().resolve(System.currentTimeMillis()
                + "-" + UUID.randomUUID() + ".json");
        JsonSupport.write(file, transfer);
    }

    public int count() {
        try {
            if (!Files.exists(AppDirectories.pending())) return 0;
            try (var files = Files.list(AppDirectories.pending())) {
                return (int) files.filter(p -> p.getFileName().toString().endsWith(".json")).count();
            }
        } catch (IOException ex) { return 0; }
    }

    public void flush(HttpReportClient client) throws IOException {
        if (!Files.exists(AppDirectories.pending()) || !client.health()) return;
        try (var files = Files.list(AppDirectories.pending())) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.naturalOrder()).toList()) {
                ReportTransfer transfer = JsonSupport.read(path, ReportTransfer.class);
                client.saveReport(transfer);
                Files.deleteIfExists(path);
            }
        }
    }
}
