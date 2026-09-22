package com.teosa.app.prototipo.application;

import com.teosa.app.prototipo.domain.AppConfig;
import com.teosa.app.prototipo.domain.ReportSnapshot;
import com.teosa.app.prototipo.domain.ReportSummary;
import com.teosa.app.prototipo.domain.ReportTransfer;
import com.teosa.app.prototipo.domain.SaveResponse;
import com.teosa.app.prototipo.domain.TemplateDefinition;
import com.teosa.app.prototipo.domain.VersionSummary;

import com.teosa.app.prototipo.domain.*;
import java.io.IOException;
import com.teosa.app.prototipo.application.port.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class AppServices {
    private final LocalDocuments queue;
    private final CatalogCache catalog;
    private final TemplateCache templates;
    private final AssetCodec assets;
    private final ConnectionRuntime runtime;
    private final List<Consumer<String>> statusListeners = new CopyOnWriteArrayList<>();
    private AppConfig config;
    private volatile RemoteDocuments client;
    private Thread backgroundThread;
    private volatile boolean running;
    private volatile String status = "Sin conexión";

    public AppServices(LocalDocuments queue, AssetCodec assets, ConnectionRuntime runtime, TemplateCache templates) {
        this(queue,assets,runtime,templates,null);
    }
    public AppServices(LocalDocuments queue, AssetCodec assets, ConnectionRuntime runtime, TemplateCache templates,CatalogCache catalog) {
        this.queue=queue; this.assets=assets; this.runtime=runtime; this.templates=templates;this.catalog=catalog;
    }

    public synchronized void initialize(AppConfig config) throws IOException {
        close();
        this.config = config;
        client=runtime.initialize(config);
        setStatus(config.getRole()==AppConfig.Role.PRIMARY ? "Servidor principal activo · "+runtime.computer()
                : client.health() ? "Conectado al servidor" : "Buscando servidor...");
        running = true;
        backgroundThread = Thread.ofVirtual().name("teosa-sync").start(this::backgroundLoop);
    }

    private void backgroundLoop() {
        while (running) {
            try {
                if (config.getRole() == AppConfig.Role.SECONDARY && !client.health()) {
                    runtime.reconnect(client, config);
                }
                if (client.health()) {
                    queue.flush(client);
                    templates.synchronize(client);
                    if(catalog!=null)catalog.synchronize(client);
                    setStatus(config.getRole() == AppConfig.Role.PRIMARY
                            ? "Servidor principal activo · " + runtime.computer()
                            : "Conectado · " + client.getBaseUrl());
                } else {
                    setStatus("Sin conexión · " + pendingSaves() + " guardado(s) pendiente(s)");
                }
            } catch (Exception ex) {
                setStatus("Sin conexión · " + pendingSaves() + " guardado(s) pendiente(s)");
            }
            try { Thread.sleep(5000); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
        }
    }

    private int pendingSaves(){
        try{return queue.count()+(catalog==null?0:catalog.pendingCount());}
        catch(IOException ex){return queue.count();}
    }
    public List<CatalogEntry> listCatalog() throws IOException {return catalog==null?List.of():catalog.list();}
    public List<CatalogEntry> refreshCatalog() throws IOException {
        try{if(catalog!=null&&client!=null)catalog.synchronize(client);}catch(IOException ignored){}
        return listCatalog();
    }
    public void updateCatalogEntry(String id,CatalogEntry entry) throws IOException {
        requireClient().updateCatalogEntry(id,entry);if(catalog!=null)catalog.synchronize(client);
    }
    public void deleteCatalogEntry(String id) throws IOException {
        requireClient().deleteCatalogEntry(id);if(catalog!=null)catalog.synchronize(client);
    }
    public boolean saveCatalogEntries(List<CatalogEntry> entries) throws IOException {
        if(catalog==null)throw new IOException("Catálogo no configurado.");
        for(CatalogEntry entry:entries)catalog.save(entry);
        try{if(client!=null&&client.health())catalog.synchronize(client);}catch(IOException ignored){}
        return catalog.pendingCount()>0;
    }
    public boolean saveCatalogEntry(CatalogEntry entry) throws IOException {
        if(catalog==null)throw new IOException("Catálogo no configurado.");
        catalog.save(entry);
        try{if(client!=null && client.health())catalog.synchronize(client);}catch(IOException ignored){}
        return catalog.pendingCount()>0;
    }

    public List<QuotationFolioChange> folioChanges()throws IOException{return queue.folioChanges();}
    public void acknowledgeFolioChange(QuotationFolioChange change)throws IOException{queue.acknowledgeFolioChange(change.reportId(),change.current());}
    public QuotationFolio quotationFolio(String id,boolean allocate){
        try{return requireClient().quotationFolio(id,allocate);}catch(IOException offline){
            String value=String.format(java.util.Locale.ROOT,"TEO%02d-PEND-%s",java.time.LocalDate.now().getYear()%100,id.substring(0,Math.min(8,id.length())).toUpperCase(java.util.Locale.ROOT));
            return new QuotationFolio(value,false);
        }
    }
    public SaveResponse saveReport(ReportSnapshot snapshot) throws IOException {
        if(snapshot.getQuotation()!=null)snapshot.getQuotation().validate();
        snapshot.setAuthor(runtime.user());
        snapshot.setComputer(runtime.computer());
        snapshot.setSavedAt(System.currentTimeMillis());
        ReportTransfer transfer = assets.pack(snapshot);
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
        setStatus("Sin conexión · " + pendingSaves() + " guardado(s) pendiente(s)");
        return response;
    }

    private RemoteDocuments requireClient() throws IOException {
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

    public List<ReportSummary> listReports(String query,DocumentKind kind)throws IOException {
        return listReports(query).stream().filter(s->s.getKind()==kind).toList();
    }
    public List<TemplateDefinition> listTemplates(DocumentKind kind)throws IOException {
        return listTemplates().stream().filter(t->t.getKind()==kind).toList();
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
            return assets.materialize(transfer);
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
    public List<TemplateDefinition> listTemplates() throws IOException { try { if(client!=null)templates.synchronize(client); } catch(IOException offline) {} return templates.list(); }
    public void saveTemplate(TemplateDefinition template) throws IOException { templates.save(template); try { if(client!=null)templates.synchronize(client); } catch(IOException offline) {} }
    public void deleteTemplate(String name) throws IOException { templates.delete(name); try { if(client!=null)templates.synchronize(client); } catch(IOException offline) {} }
    public boolean isConnected() { return client != null && client.health(); }
    public String getStatus() { return status; }
    public AppConfig getConfig() { return config; }

    public void addStatusListener(Consumer<String> listener) {
        statusListeners.add(listener);
        listener.accept(status);
    }

    public void removeStatusListener(Consumer<String> listener){statusListeners.remove(listener);}

    private void setStatus(String status) {
        if (status.equals(this.status)) return;
        this.status = status;
        for (Consumer<String> listener : statusListeners) listener.accept(status);
    }

    public synchronized void close() {
        running = false;
        if (backgroundThread != null) backgroundThread.interrupt();
        runtime.close();
        backgroundThread = null;
        client = null;
    }
}
