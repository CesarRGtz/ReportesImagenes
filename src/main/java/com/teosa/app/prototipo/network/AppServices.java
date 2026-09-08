package com.teosa.app.prototipo.network;

import com.teosa.app.prototipo.data.*;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class AppServices {
    private static final AppServices INSTANCE = new AppServices();
    private final OfflineQueue queue = new OfflineQueue();
    private final List<Consumer<String>> statusListeners = new CopyOnWriteArrayList<>();
    private AppConfig config;
    private HttpReportClient client;
    private LocalReportServer server;
    private DiscoveryAnnouncer announcer;
    private Thread backgroundThread;
    private volatile boolean running;
    private volatile String status = "Sin conexión";

    private AppServices() {}
    public static AppServices get() { return INSTANCE; }

    public synchronized void initialize(AppConfig config) throws IOException {
        close();
        this.config = config;
        if (config.getRole() == AppConfig.Role.PRIMARY) {
            ServerStorage storage = new ServerStorage(AppDirectories.serverData());
            try {
                server = new LocalReportServer(config.getServerPort(), storage);
            } catch (IOException portInUse) {
                server = new LocalReportServer(0, storage);
            }
            server.start();
            announcer = new DiscoveryAnnouncer(server.getPort());
            announcer.start();
            String url = "http://127.0.0.1:" + server.getPort();
            client = new HttpReportClient(url);
            config.setServerUrl(url);
            ConfigStore.save(config);
            setStatus("Servidor principal activo · " + UserIdentity.computer());
        } else {
            client = new HttpReportClient(config.getServerUrl());
            setStatus(client.health() ? "Conectado al servidor" : "Buscando servidor...");
        }
        running = true;
        backgroundThread = Thread.ofVirtual().name("teosa-sync").start(this::backgroundLoop);
    }

    private void backgroundLoop() {
        while (running) {
            try {
                if (config.getRole() == AppConfig.Role.SECONDARY && !client.health()) {
                    String discovered = DiscoveryClient.discover(Duration.ofSeconds(4));
                    if (discovered != null) {
                        client.setBaseUrl(discovered);
                        config.setServerUrl(discovered);
                        ConfigStore.save(config);
                    }
                }
                if (client.health()) {
                    queue.flush(client);
                    setStatus(config.getRole() == AppConfig.Role.PRIMARY
                            ? "Servidor principal activo · " + UserIdentity.computer()
                            : "Conectado · " + client.getBaseUrl());
                } else {
                    setStatus("Sin conexión · " + queue.count() + " guardado(s) pendiente(s)");
                }
            } catch (Exception ex) {
                setStatus("Sin conexión · " + queue.count() + " guardado(s) pendiente(s)");
            }
            try { Thread.sleep(5000); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
        }
    }

    public SaveResponse saveReport(ReportSnapshot snapshot) throws IOException {
        snapshot.setAuthor(UserIdentity.user());
        snapshot.setComputer(UserIdentity.computer());
        snapshot.setSavedAt(System.currentTimeMillis());
        ReportTransfer transfer = AssetManager.pack(snapshot);
        if (config != null && config.getRole() == AppConfig.Role.SECONDARY) {
            if (client == null) {
                queue.enqueue(transfer);
                return queuedResponse(snapshot);
            }
            SaveResponse response = queue.saveLocallyAndTryUpload(transfer, client);
            if (response != null) {
                setStatus("Conectado · reporte guardado localmente y sincronizado");
                return response;
            }
            return queuedResponse(snapshot);
        }
        if (client == null) return queueAndRespond(snapshot, transfer);
        try {
            SaveResponse response = client.saveReport(transfer);
            setStatus("Conectado · reporte guardado");
            return response;
        } catch (IOException ex) {
            return queueAndRespond(snapshot, transfer);
        }
    }

    private SaveResponse queueAndRespond(ReportSnapshot snapshot, ReportTransfer transfer)
            throws IOException {
        queue.enqueue(transfer);
        return queuedResponse(snapshot);
    }

    private SaveResponse queuedResponse(ReportSnapshot snapshot) {
        SaveResponse response = new SaveResponse();
        response.setSuccess(true);
        response.setQueued(true);
        response.setReportId(snapshot.getReportId());
        response.setMessage("Servidor no disponible. El reporte quedó pendiente de sincronización.");
        setStatus("Sin conexión · " + queue.count() + " guardado(s) pendiente(s)");
        return response;
    }

    private HttpReportClient requireClient() throws IOException {
        if (client == null) throw new IOException("El servicio de reportes todavía no está disponible");
        return client;
    }

    public List<ReportSummary> listReports(String query) throws IOException {
        List<ReportSummary> local = queue.listReports(query);
        List<ReportSummary> remote = new ArrayList<>();
        if (client != null) {
            try { remote.addAll(client.listReports(query)); }
            catch (IOException ignored) { /* El historial local sigue disponible. */ }
        }
        Map<String, ReportSummary> merged = new LinkedHashMap<>();
        for (ReportSummary summary : remote) merged.put(summary.getReportId(), summary);
        for (ReportSummary localSummary : local) {
            ReportSummary serverSummary = merged.get(localSummary.getReportId());
            if (serverSummary == null) {
                merged.put(localSummary.getReportId(), localSummary);
                continue;
            }
            serverSummary.setPendingCount(localSummary.getPendingCount());
            serverSummary.setVersionCount(serverSummary.getVersionCount()
                    + localSummary.getPendingCount());
            if (localSummary.isPending()
                    && localSummary.getModifiedAt() > serverSummary.getModifiedAt()) {
                serverSummary.setClient(localSummary.getClient());
                serverSummary.setDate(localSummary.getDate());
                serverSummary.setArea(localSummary.getArea());
                serverSummary.setRemision(localSummary.getRemision());
                serverSummary.setModifiedAt(localSummary.getModifiedAt());
                serverSummary.setLastAuthor(localSummary.getLastAuthor());
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingLong(ReportSummary::getModifiedAt).reversed())
                .toList();
    }

    public List<VersionSummary> listVersions(String id) throws IOException {
        List<VersionSummary> remote = new ArrayList<>();
        if (client != null) {
            try { remote.addAll(client.listVersions(id)); }
            catch (IOException ignored) { /* Se muestran las copias locales. */ }
        }
        List<VersionSummary> local = queue.listVersions(id);
        Map<Integer, VersionSummary> remoteByVersion = new LinkedHashMap<>();
        for (VersionSummary version : remote) remoteByVersion.put(version.getVersion(), version);
        List<VersionSummary> merged = new ArrayList<>(remote);
        for (VersionSummary version : local) {
            if (version.isPending() || !remoteByVersion.containsKey(version.getVersion())) {
                merged.add(version);
            }
        }
        merged.sort(Comparator.comparingLong(VersionSummary::getSavedAt).reversed());
        return merged;
    }

    public ReportSnapshot loadReport(String id, int version) throws IOException {
        try {
            ReportTransfer transfer = requireClient().loadReport(id, version);
            if (config != null && config.getRole() == AppConfig.Role.SECONDARY) {
                queue.cacheSynced(transfer);
            }
            return AssetManager.materialize(transfer);
        } catch (IOException ex) {
            return queue.loadVersion(id, version);
        }
    }

    public ReportSnapshot loadReport(String id, VersionSummary version) throws IOException {
        if (version.getLocalId() != null && (version.isPending() || !isConnected())) {
            return queue.load(version.getLocalId());
        }
        try {
            return loadReport(id, version.getVersion());
        } catch (IOException ex) {
            if (version.getLocalId() != null) return queue.load(version.getLocalId());
            throw ex;
        }
    }

    public void deleteReport(String id) throws IOException {
        if (isConnected()) requireClient().deleteReport(id);
        queue.deleteLocalReport(id);
    }

    public void deleteVersion(String id, int version) throws IOException {
        if (isConnected()) requireClient().deleteVersion(id, version);
        queue.deleteSyncedVersion(id, version);
    }

    public void deleteVersion(String id, VersionSummary version) throws IOException {
        if (version.isPending() || (!isConnected() && version.getLocalId() != null)) {
            queue.deleteLocalVersion(version.getLocalId());
            return;
        }
        deleteVersion(id, version.getVersion());
    }
    public List<TemplateDefinition> listTemplates() throws IOException { return requireClient().listTemplates(); }
    public void saveTemplate(TemplateDefinition template) throws IOException { requireClient().saveTemplate(template); }
    public void deleteTemplate(String name) throws IOException { requireClient().deleteTemplate(name); }
    public boolean isConnected() { return client != null && client.health(); }
    public String getStatus() { return status; }
    public AppConfig getConfig() { return config; }

    public void addStatusListener(Consumer<String> listener) {
        statusListeners.add(listener);
        listener.accept(status);
    }

    private void setStatus(String status) {
        if (status.equals(this.status)) return;
        this.status = status;
        for (Consumer<String> listener : statusListeners) listener.accept(status);
    }

    public synchronized void close() {
        running = false;
        if (backgroundThread != null) backgroundThread.interrupt();
        if (announcer != null) announcer.close();
        if (server != null) server.close();
        backgroundThread = null;
        announcer = null;
        server = null;
        client = null;
    }
}
