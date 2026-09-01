package com.teosa.app.prototipo.network;

import com.teosa.app.prototipo.data.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.security.MessageDigest;

public class ServerStorage {
    private final Path root;
    private final Path reports;
    private final Path templates;

    public ServerStorage(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.reports = this.root.resolve("reportes");
        this.templates = this.root.resolve("plantillas");
        Files.createDirectories(reports);
        Files.createDirectories(templates);
    }

    public synchronized SaveResponse saveReport(ReportTransfer transfer) throws IOException {
        ReportSnapshot snapshot = transfer.getSnapshot();
        if (snapshot == null || snapshot.getReport() == null) throw new IOException("Reporte vacío");
        String reportId = normalizeId(snapshot.getReportId());
        Path reportDir = reports.resolve(reportId);
        Path versionsDir = reportDir.resolve("versiones");
        Files.createDirectories(versionsDir);
        int version = nextVersion(versionsDir);
        snapshot.setReportId(reportId);
        snapshot.setVersion(version);
        snapshot.setSavedAt(System.currentTimeMillis());

        Path assetDir = reportDir.resolve("imagenes").resolve("v" + version);
        Files.createDirectories(assetDir);
        for (AssetPayload asset : transfer.getAssets()) {
            String fileName = AssetManager.safe(asset.getAssetId())
                    + AssetManager.extension(asset.getFileName());
            Files.write(assetDir.resolve(fileName),
                    Base64.getDecoder().decode(asset.getDataBase64()));
        }
        JsonSupport.write(versionsDir.resolve(String.format("version-%05d.json", version)), snapshot);
        backupIfNeeded();

        SaveResponse response = new SaveResponse();
        response.setSuccess(true);
        response.setReportId(reportId);
        response.setVersion(version);
        response.setMessage("Reporte guardado como versión " + version);
        return response;
    }

    public synchronized List<ReportSummary> listReports(String query) throws IOException {
        List<ReportSummary> result = new ArrayList<>();
        if (!Files.exists(reports)) return result;
        try (var directories = Files.list(reports)) {
            for (Path reportDir : directories.filter(Files::isDirectory).toList()) {
                List<Path> versions = versionFiles(reportDir.resolve("versiones"));
                if (versions.isEmpty()) continue;
                ReportSnapshot latest = JsonSupport.read(versions.get(versions.size() - 1), ReportSnapshot.class);
                ReportSummary summary = new ReportSummary();
                summary.setReportId(latest.getReportId());
                summary.setClient(latest.getReport().getCliente());
                summary.setDate(latest.getReport().getFecha());
                summary.setArea(latest.getReport().getArea());
                summary.setRemision(latest.getReport().getRemision());
                summary.setModifiedAt(latest.getSavedAt());
                summary.setVersionCount(versions.size());
                summary.setLastAuthor(latest.getAuthor());
                if (matches(summary, query)) result.add(summary);
            }
        }
        result.sort(Comparator.comparingLong(ReportSummary::getModifiedAt).reversed());
        return result;
    }

    public synchronized List<VersionSummary> listVersions(String reportId) throws IOException {
        List<VersionSummary> result = new ArrayList<>();
        for (Path path : versionFiles(reportPath(reportId).resolve("versiones"))) {
            ReportSnapshot snapshot = JsonSupport.read(path, ReportSnapshot.class);
            VersionSummary version = new VersionSummary();
            version.setVersion(snapshot.getVersion());
            version.setSavedAt(snapshot.getSavedAt());
            version.setAuthor(snapshot.getAuthor());
            version.setComputer(snapshot.getComputer());
            result.add(version);
        }
        result.sort(Comparator.comparingInt(VersionSummary::getVersion).reversed());
        return result;
    }

    public synchronized ReportTransfer loadReport(String reportId, int version) throws IOException {
        Path reportDir = reportPath(reportId);
        Path snapshotPath = reportDir.resolve("versiones")
                .resolve(String.format("version-%05d.json", version));
        if (!Files.exists(snapshotPath)) throw new IOException("Versión no encontrada");
        ReportTransfer transfer = new ReportTransfer();
        transfer.setSnapshot(JsonSupport.read(snapshotPath, ReportSnapshot.class));
        Path assetDir = reportDir.resolve("imagenes").resolve("v" + version);
        if (Files.exists(assetDir)) {
            try (var assets = Files.list(assetDir)) {
                for (Path path : assets.filter(Files::isRegularFile).toList()) {
                    String fileName = path.getFileName().toString();
                    int dot = fileName.lastIndexOf('.');
                    AssetPayload payload = new AssetPayload();
                    payload.setAssetId(dot < 0 ? fileName : fileName.substring(0, dot));
                    payload.setFileName(fileName);
                    payload.setOriginal(payload.getAssetId().endsWith("-original"));
                    payload.setDataBase64(Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
                    transfer.getAssets().add(payload);
                }
            }
        }
        return transfer;
    }

    public synchronized void deleteReport(String reportId) throws IOException {
        deleteRecursively(reportPath(reportId));
        backupIfNeeded();
    }

    public synchronized void deleteVersion(String reportId, int version) throws IOException {
        Path reportDir = reportPath(reportId);
        Files.deleteIfExists(reportDir.resolve("versiones")
                .resolve(String.format("version-%05d.json", version)));
        deleteRecursively(reportDir.resolve("imagenes").resolve("v" + version));
        if (versionFiles(reportDir.resolve("versiones")).isEmpty()) deleteRecursively(reportDir);
        backupIfNeeded();
    }

    public synchronized List<TemplateDefinition> listTemplates() throws IOException {
        List<TemplateDefinition> result = new ArrayList<>();
        if (Files.exists(templates)) {
            try (var paths = Files.list(templates)) {
                for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                    result.add(JsonSupport.read(path, TemplateDefinition.class));
                }
            }
        }
        result.sort(Comparator.comparingLong(TemplateDefinition::getLastUsedAt).reversed());
        return result;
    }

    public synchronized void saveTemplate(TemplateDefinition template) throws IOException {
        if (template.getName().isBlank()) throw new IOException("La plantilla necesita nombre");
        Path target = templates.resolve(safeName(template.getName()) + ".json");
        JsonSupport.write(target, template);
        backupIfNeeded();
    }

    public synchronized void deleteTemplate(String name) throws IOException {
        Files.deleteIfExists(templates.resolve(safeName(name) + ".json"));
        backupIfNeeded();
    }

    private Path reportPath(String id) throws IOException {
        Path path = reports.resolve(normalizeId(id)).normalize();
        if (!path.startsWith(reports)) throw new IOException("Identificador inválido");
        return path;
    }

    private String normalizeId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{8,80}")) return UUID.randomUUID().toString();
        return id;
    }

    private int nextVersion(Path versionsDir) throws IOException {
        return versionFiles(versionsDir).stream().mapToInt(this::versionFromPath).max().orElse(0) + 1;
    }

    private List<Path> versionFiles(Path versionsDir) throws IOException {
        if (!Files.exists(versionsDir)) return new ArrayList<>();
        try (var paths = Files.list(versionsDir)) {
            return paths.filter(p -> p.getFileName().toString().matches("version-\\d+\\.json"))
                    .sorted(Comparator.comparingInt(this::versionFromPath)).toList();
        }
    }

    private int versionFromPath(Path path) {
        return Integer.parseInt(path.getFileName().toString().replaceAll("\\D", ""));
    }

    private boolean matches(ReportSummary summary, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase(Locale.ROOT);
        return (summary.getClient() + " " + summary.getDate() + " " + summary.getArea()
                + " " + summary.getRemision() + " " + summary.getLastAuthor())
                .toLowerCase(Locale.ROOT).contains(q);
    }

    private String safeName(String value) {
        String clean = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9_-]+", "-")
                .replaceAll("^-|-$", "").toLowerCase(Locale.ROOT);
        if (clean.isBlank()) clean = "plantilla";
        return clean + "-" + nameDigest(value);
    }

    private String nameDigest(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().toLowerCase(Locale.ROOT)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 6);
        } catch (Exception impossible) {
            return Integer.toUnsignedString(value.toLowerCase(Locale.ROOT).hashCode(), 36);
        }
    }

    private void backupIfNeeded() throws IOException {
        Files.createDirectories(AppDirectories.backups());
        Path target = AppDirectories.backups().resolve("respaldo-" + LocalDate.now() + ".zip");
        if (Files.exists(target)) return;
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            if (Files.exists(root)) {
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.filter(Files::isRegularFile).toList()) {
                        zip.putNextEntry(new ZipEntry(root.relativize(path).toString().replace('\\', '/')));
                        Files.copy(path, zip);
                        zip.closeEntry();
                    }
                }
            }
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteRecursively(Path target) throws IOException {
        if (!Files.exists(target)) return;
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) throw new IOException("Ruta de eliminación inválida");
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
