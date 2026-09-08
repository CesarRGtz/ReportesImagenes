package com.teosa.app.prototipo;

import com.teosa.app.prototipo.data.*;
import com.teosa.app.prototipo.network.*;
import java.net.ServerSocket;
import java.nio.file.Files;

public class AppServicesOfflineSmokeTest {
    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("teosa-services-offline-test-");
        System.setProperty("teosa.data.dir", directory.toString());
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        AppConfig config = new AppConfig();
        config.setRole(AppConfig.Role.SECONDARY);
        config.setServerUrl("http://127.0.0.1:" + port);
        AppServices services = AppServices.get();
        services.initialize(config);
        try {
            ReportSnapshot snapshot = new ReportSnapshot();
            snapshot.setReportId("servicio-local-1234");
            snapshot.setReport(new ReporteServicio("Cliente secundario", "08/09/2026",
                    "Taller", "R-3", "C-3", "F-3", "Equipo", "Trabajo"));
            snapshot.setTemplate(TemplateDefinition.defaults());
            SaveResponse response = services.saveReport(snapshot);
            if (!response.isQueued()) {
                throw new AssertionError("El guardado sin servidor no quedó pendiente");
            }
            ReportSummary pendingReport = services.listReports("").getFirst();
            VersionSummary pendingVersion = services.listVersions(
                    snapshot.getReportId()).getFirst();
            if (!pendingReport.isPending() || !pendingVersion.isPending()) {
                throw new AssertionError("El historial no mostró la marca de pendiente");
            }
            if (!"Cliente secundario".equals(services.loadReport(
                    snapshot.getReportId(), pendingVersion).getReport().getCliente())) {
                throw new AssertionError("No fue posible abrir el reporte local pendiente");
            }

            ServerStorage storage = new ServerStorage(directory.resolve("servidor"));
            try (LocalReportServer server = new LocalReportServer(port, storage)) {
                server.start();
                long deadline = System.currentTimeMillis() + 12_000;
                boolean synchronizedReport = false;
                while (System.currentTimeMillis() < deadline) {
                    var reports = services.listReports("");
                    if (reports.size() == 1 && !reports.getFirst().isPending()
                            && reports.getFirst().getVersionCount() == 1
                            && services.listVersions(snapshot.getReportId()).size() == 1) {
                        synchronizedReport = true;
                        break;
                    }
                    Thread.sleep(200);
                }
                if (!synchronizedReport) {
                    throw new AssertionError("El reporte no se subió al recuperar la conexión");
                }
            }

            services.close();
            ReportSummary localCopy = services.listReports("").getFirst();
            VersionSummary localVersion = services.listVersions(
                    snapshot.getReportId()).getFirst();
            if (localCopy.isPending() || localVersion.isPending()
                    || !"Cliente secundario".equals(services.loadReport(
                    snapshot.getReportId(), localVersion).getReport().getCliente())) {
                throw new AssertionError("La copia sincronizada no quedó accesible sin servidor");
            }
        } finally {
            services.close();
        }
        System.out.println("APP_SERVICES_OFFLINE_OK");
    }
}
