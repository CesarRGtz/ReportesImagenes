package com.teosa.app.prototipo.infrastructure.network;

import com.teosa.app.prototipo.infrastructure.persistence.AppDirectories;
import com.teosa.app.prototipo.infrastructure.persistence.AssetManager;
import com.teosa.app.prototipo.infrastructure.persistence.JsonSupport;
import com.teosa.app.prototipo.domain.ReportSnapshot;
import com.teosa.app.prototipo.domain.ReportSummary;
import com.teosa.app.prototipo.domain.ReportTransfer;
import com.teosa.app.prototipo.domain.SaveResponse;
import com.teosa.app.prototipo.domain.VersionSummary;

import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import java.io.IOException;
import com.teosa.app.prototipo.application.port.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class OfflineQueue implements LocalDocuments {
    private static final class FolioNotices {Map<String,QuotationFolioChange> changes=new LinkedHashMap<>();}
    private Path folioNoticesPath(){return AppDirectories.base().resolve("cambios-folios.json");}
    private FolioNotices readFolioNotices()throws IOException{return Files.exists(folioNoticesPath())?JsonSupport.read(folioNoticesPath(),FolioNotices.class):new FolioNotices();}
    public synchronized List<QuotationFolioChange> folioChanges()throws IOException{return List.copyOf(readFolioNotices().changes.values());}
    public synchronized void acknowledgeFolioChange(String reportId,String current)throws IOException{
        FolioNotices notices=readFolioNotices();QuotationFolioChange change=notices.changes.get(reportId);
        if(change!=null&&change.current().equals(current)){notices.changes.remove(reportId);JsonSupport.write(folioNoticesPath(),notices);}
    }
    private void recordFolioChange(String id,String previous,String current)throws IOException{
        if(previous.equals(current))return;
        FolioNotices notices=readFolioNotices();QuotationFolioChange old=notices.changes.get(id);
        notices.changes.put(id,new QuotationFolioChange(id,old==null?previous:old.previous(),current));JsonSupport.write(folioNoticesPath(),notices);
    }
    private static final String PENDING_PREFIX = "pending:";
    private static final String LOCAL_PREFIX = "local:";

    public synchronized String enqueue(ReportTransfer transfer) throws IOException {
        if (transfer == null || transfer.getSnapshot() == null) {
            throw new IOException("El reporte local está vacío");
        }
        if (transfer.getSnapshot().getSavedAt() <= 0) {
            transfer.getSnapshot().setSavedAt(System.currentTimeMillis());
        }
        String fileName = System.currentTimeMillis() + "-" + UUID.randomUUID() + ".json";
        JsonSupport.write(AppDirectories.pending().resolve(fileName), transfer);
        return PENDING_PREFIX + fileName;
    }

    public synchronized int count() {
        try {
            return entries(AppDirectories.pending(), true).size();
        } catch (IOException ex) {
            return 0;
        }
    }

    public synchronized SaveResponse upload(String localId, RemoteDocuments client)
            throws IOException {
        Path path = resolveLocalId(localId, true);
        ReportTransfer transfer = JsonSupport.read(path, ReportTransfer.class);
        SaveResponse response = client.saveReport(transfer);
        archiveSynced(transfer, response);
        Files.deleteIfExists(path);
        return response;
    }

    public synchronized SaveResponse saveLocallyAndTryUpload(
            ReportTransfer transfer, RemoteDocuments client) throws IOException {
        String localId = enqueue(transfer);
        try {
            return upload(localId, client);
        } catch (IOException ex) {
            return null;
        }
    }

    public synchronized void flush(RemoteDocuments client) throws IOException {
        if (!Files.exists(AppDirectories.pending()) || !client.health()) return;
        for (LocalEntry entry : entries(AppDirectories.pending(), true)) {
            SaveResponse response = client.saveReport(entry.transfer());
            archiveSynced(entry.transfer(), response);
            Files.deleteIfExists(entry.path());
        }
    }

    public synchronized List<ReportSummary> listReports(String query) throws IOException {
        Map<String, List<LocalEntry>> grouped = new LinkedHashMap<>();
        for (LocalEntry entry : allEntries()) {
            ReportSnapshot snapshot = entry.transfer().getSnapshot();
            if (snapshot == null || !snapshot.hasDocument()
                    || snapshot.getReportId() == null) continue;
            grouped.computeIfAbsent(snapshot.getReportId(), ignored -> new ArrayList<>())
                    .add(entry);
        }

        List<ReportSummary> result = new ArrayList<>();
        for (Map.Entry<String, List<LocalEntry>> group : grouped.entrySet()) {
            List<LocalEntry> versions = group.getValue();
            LocalEntry latest = versions.stream().max(Comparator.comparingLong(entry ->
                    entry.transfer().getSnapshot().getSavedAt())).orElseThrow();
            ReportSnapshot snapshot = latest.transfer().getSnapshot();
            ReportSummary summary = new ReportSummary();
            summary.setReportId(group.getKey());
            summary.setKind(snapshot.getKind());
            summary.setClient(snapshot.client());
            summary.setDate(snapshot.date());
            summary.setArea(snapshot.area());
            summary.setRemision(snapshot.reference());
            summary.setModifiedAt(snapshot.getSavedAt());
            summary.setVersionCount(versions.size());
            summary.setPendingCount((int) versions.stream().filter(LocalEntry::pending).count());
            summary.setLastAuthor(snapshot.getAuthor());
            if (matches(summary, query)) result.add(summary);
        }
        result.sort(Comparator.comparingLong(ReportSummary::getModifiedAt).reversed());
        return result;
    }

    public synchronized List<VersionSummary> listVersions(String reportId) throws IOException {
        List<VersionSummary> result = new ArrayList<>();
        for (LocalEntry entry : allEntries()) {
            ReportSnapshot snapshot = entry.transfer().getSnapshot();
            if (snapshot == null || !reportId.equals(snapshot.getReportId())) continue;
            VersionSummary version = new VersionSummary();
            version.setVersion(snapshot.getVersion());
            version.setSavedAt(snapshot.getSavedAt());
            version.setAuthor(snapshot.getAuthor());
            version.setComputer(snapshot.getComputer());
            version.setPending(entry.pending());
            version.setLocalId((entry.pending() ? PENDING_PREFIX : LOCAL_PREFIX)
                    + entry.path().getFileName());
            result.add(version);
        }
        result.sort(Comparator.comparingLong(VersionSummary::getSavedAt).reversed());
        return result;
    }

    public synchronized ReportSnapshot load(String localId) throws IOException {
        return AssetManager.materialize(JsonSupport.read(resolveLocalId(localId, false),
                ReportTransfer.class));
    }

    public synchronized ReportSnapshot loadVersion(String reportId, int version)
            throws IOException {
        for (LocalEntry entry : entries(AppDirectories.localReports(), false)) {
            ReportSnapshot snapshot = entry.transfer().getSnapshot();
            if (snapshot != null && reportId.equals(snapshot.getReportId())
                    && snapshot.getVersion() == version) {
                return AssetManager.materialize(entry.transfer());
            }
        }
        throw new IOException("La versión no está guardada en este equipo");
    }

    public synchronized void cacheSynced(ReportTransfer transfer) throws IOException {
        ReportSnapshot snapshot = transfer == null ? null : transfer.getSnapshot();
        if (snapshot == null || snapshot.getReportId() == null || snapshot.getVersion() <= 0) return;
        JsonSupport.write(syncedPath(snapshot.getReportId(), snapshot.getVersion()), transfer);
    }

    public synchronized void deleteLocalVersion(String localId) throws IOException {
        Files.deleteIfExists(resolveLocalId(localId, false));
    }

    public synchronized void deleteSyncedVersion(String reportId, int version) throws IOException {
        for (LocalEntry entry : entries(AppDirectories.localReports(), false)) {
            ReportSnapshot snapshot = entry.transfer().getSnapshot();
            if (snapshot != null && reportId.equals(snapshot.getReportId())
                    && snapshot.getVersion() == version) Files.deleteIfExists(entry.path());
        }
    }

    public synchronized void deleteLocalReport(String reportId) throws IOException {
        for (LocalEntry entry : allEntries()) {
            ReportSnapshot snapshot = entry.transfer().getSnapshot();
            if (snapshot != null && reportId.equals(snapshot.getReportId())) {
                Files.deleteIfExists(entry.path());
            }
        }
    }

    private void archiveSynced(ReportTransfer transfer, SaveResponse response) throws IOException {
        ReportSnapshot snapshot = transfer.getSnapshot();
        snapshot.setReportId(response.getReportId());
        if(snapshot.getQuotation()!=null&&response.getQuotationFolio()!=null){
            recordFolioChange(snapshot.getReportId(),snapshot.getQuotation().value("folio"),response.getQuotationFolio());
            snapshot.getQuotation().setValue("folio",response.getQuotationFolio());snapshot.getQuotation().setFolioAssigned(true);
        }
        snapshot.setVersion(response.getVersion());
        snapshot.setSavedAt(System.currentTimeMillis());
        JsonSupport.write(syncedPath(snapshot.getReportId(), snapshot.getVersion()), transfer);
    }

    private Path syncedPath(String reportId, int version) {
        return AppDirectories.localReports().resolve(
                AssetManager.safe(reportId) + "-v" + version + ".json");
    }

    private List<LocalEntry> allEntries() throws IOException {
        List<LocalEntry> result = new ArrayList<>();
        result.addAll(entries(AppDirectories.pending(), true));
        result.addAll(entries(AppDirectories.localReports(), false));
        return result;
    }

    private List<LocalEntry> entries(Path directory, boolean pending) throws IOException {
        List<LocalEntry> result = new ArrayList<>();
        if (!Files.exists(directory)) return result;
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.naturalOrder()).toList()) {
                try {
                    result.add(new LocalEntry(path,
                            JsonSupport.read(path, ReportTransfer.class), pending));
                } catch (Exception ignored) {
                    // Un archivo dañado no debe ocultar los demás reportes locales.
                }
            }
        }
        return result;
    }

    private Path resolveLocalId(String localId, boolean requirePending) throws IOException {
        if (localId == null) throw new IOException("Versión local no encontrada");
        boolean pending = localId.startsWith(PENDING_PREFIX);
        boolean local = localId.startsWith(LOCAL_PREFIX);
        if ((!pending && !local) || (requirePending && !pending)) {
            throw new IOException("Identificador local inválido");
        }
        String fileName = localId.substring(localId.indexOf(':') + 1);
        if (!Path.of(fileName).getFileName().toString().equals(fileName)) {
            throw new IOException("Identificador local inválido");
        }
        Path root = (pending ? AppDirectories.pending() : AppDirectories.localReports())
                .toAbsolutePath().normalize();
        Path path = root.resolve(fileName).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new IOException("Versión local no encontrada");
        }
        return path;
    }

    private boolean matches(ReportSummary summary, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase(Locale.ROOT);
        return (summary.getClient() + " " + summary.getDate() + " " + summary.getArea()
                + " " + summary.getRemision() + " " + summary.getLastAuthor())
                .toLowerCase(Locale.ROOT).contains(q);
    }

    private record LocalEntry(Path path, ReportTransfer transfer, boolean pending) {}
}
