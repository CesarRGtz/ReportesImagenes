package com.teosa.app.prototipo.data;

import com.teosa.app.prototipo.CategoriaFotografica;
import com.teosa.app.prototipo.FotoEvidencia;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AssetManager {
    private AssetManager() {}

    public static ReportTransfer pack(ReportSnapshot original) throws IOException {
        ReportSnapshot snapshot = JsonSupport.GSON.fromJson(
                JsonSupport.GSON.toJson(original), ReportSnapshot.class);
        ReportTransfer transfer = new ReportTransfer();
        transfer.setSnapshot(snapshot);
        int categoryIndex = 0;
        for (CategoriaFotografica category : snapshot.getReport().getCategoriasFotograficas()) {
            int photoIndex = 0;
            for (FotoEvidencia photo : category.getFotografias()) {
                String baseId = "c" + categoryIndex + "-p" + photoIndex;
                String currentPath = photo.getRuta();
                if (currentPath != null && !currentPath.startsWith("asset:")) {
                    transfer.getAssets().add(toPayload(baseId, Path.of(currentPath), false));
                    photo.setRuta("asset:" + baseId);
                }
                String originalPath = photo.getRutaOriginal();
                if (originalPath != null && !originalPath.equals(currentPath)
                        && !originalPath.startsWith("asset:")) {
                    String originalId = baseId + "-original";
                    transfer.getAssets().add(toPayload(originalId, Path.of(originalPath), true));
                    photo.setRutaOriginal("asset:" + originalId);
                } else if (originalPath != null && originalPath.equals(currentPath)) {
                    photo.setRutaOriginal("asset:" + baseId);
                }
                photoIndex++;
            }
            categoryIndex++;
        }
        return transfer;
    }

    private static AssetPayload toPayload(String id, Path path, boolean original) throws IOException {
        AssetPayload payload = new AssetPayload();
        payload.setAssetId(id);
        payload.setFileName(path.getFileName().toString());
        payload.setOriginal(original);
        payload.setDataBase64(Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
        return payload;
    }

    public static ReportSnapshot materialize(ReportTransfer transfer) throws IOException {
        ReportSnapshot snapshot = transfer.getSnapshot();
        Path directory = AppDirectories.cache().resolve("reportes")
                .resolve(snapshot.getReportId()).resolve("v" + snapshot.getVersion());
        Files.createDirectories(directory);
        Map<String, Path> paths = new LinkedHashMap<>();
        for (AssetPayload asset : transfer.getAssets()) {
            String extension = extension(asset.getFileName());
            Path output = directory.resolve(safe(asset.getAssetId()) + extension);
            Files.write(output, Base64.getDecoder().decode(asset.getDataBase64()));
            paths.put(asset.getAssetId(), output);
        }
        for (CategoriaFotografica category : snapshot.getReport().getCategoriasFotograficas()) {
            for (FotoEvidencia photo : category.getFotografias()) {
                photo.setRuta(resolveMarker(photo.getRuta(), paths));
                photo.setRutaOriginal(resolveMarker(photo.getRutaOriginal(), paths));
            }
        }
        return snapshot;
    }

    private static String resolveMarker(String value, Map<String, Path> paths) {
        if (value == null || !value.startsWith("asset:")) return value;
        Path path = paths.get(value.substring(6));
        return path == null ? value : path.toAbsolutePath().toString();
    }

    public static String extension(String name) {
        if (name == null) return ".bin";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? ".bin" : name.substring(dot).replaceAll("[^A-Za-z0-9.]", "");
    }

    public static String safe(String value) {
        return value == null ? "item" : value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
