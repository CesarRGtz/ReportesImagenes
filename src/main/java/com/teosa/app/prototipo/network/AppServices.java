package com.teosa.app.prototipo.network;

import com.teosa.app.prototipo.data.*;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
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
        Thread.ofVirtual().name("teosa-sync").start(this::backgroundLoop);
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
        ReportTransfer transfer = AssetManager.pack(snapshot);
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

    public List<ReportSummary> listReports(String query) throws IOException { return requireClient().listReports(query); }
    public List<VersionSummary> listVersions(String id) throws IOException { return requireClient().listVersions(id); }
    public ReportSnapshot loadReport(String id, int version) throws IOException {
        return AssetManager.materialize(requireClient().loadReport(id, version));
    }
    public void deleteReport(String id) throws IOException { requireClient().deleteReport(id); }
    public void deleteVersion(String id, int version) throws IOException { requireClient().deleteVersion(id, version); }
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
        if (announcer != null) announcer.close();
        if (server != null) server.close();
        announcer = null;
        server = null;
        client = null;
    }
}
