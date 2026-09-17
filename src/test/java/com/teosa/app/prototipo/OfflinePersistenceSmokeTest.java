package com.teosa.app.prototipo;

import com.teosa.app.prototipo.domain.CategoriaFotografica;
import com.teosa.app.prototipo.domain.FotoEvidencia;
import com.teosa.app.prototipo.domain.ReporteServicio;
import com.teosa.app.prototipo.infrastructure.persistence.AssetManager;
import com.teosa.app.prototipo.domain.ReportSnapshot;
import com.teosa.app.prototipo.domain.ReportSummary;
import com.teosa.app.prototipo.domain.TemplateDefinition;
import com.teosa.app.prototipo.domain.VersionSummary;
import com.teosa.app.prototipo.infrastructure.network.HttpReportClient;
import com.teosa.app.prototipo.infrastructure.network.LocalReportServer;
import com.teosa.app.prototipo.infrastructure.network.OfflineQueue;
import com.teosa.app.prototipo.infrastructure.network.ServerStorage;

import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import com.teosa.app.prototipo.infrastructure.network.*;
import com.teosa.app.prototipo.application.AppServices;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public class OfflinePersistenceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("teosa-offline-test-");
        System.setProperty("teosa.data.dir", directory.toString());
        Path imagePath = directory.resolve("evidencia.png");
        BufferedImage image = new BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ImageIO.write(image, "png", imagePath.toFile());

        ReporteServicio report = new ReporteServicio("Cliente local", "08/09/2026",
                "Taller", "R-2", "C-2", "F-2", "Motor", "Guardado sin conexión");
        CategoriaFotografica category = new CategoriaFotografica("Evidencia local");
        category.agregarFotografia(new FotoEvidencia(imagePath.toString(), "Detalle"));
        report.agregarCategoriaFotografica(category);
        ReportSnapshot snapshot = new ReportSnapshot();
        snapshot.setReportId("reporte-local-1234");
        snapshot.setSavedAt(System.currentTimeMillis());
        snapshot.setAuthor("operador");
        snapshot.setComputer("secundaria-prueba");
        snapshot.setReport(report);
        snapshot.setTemplate(TemplateDefinition.defaults());

        OfflineQueue queue = new OfflineQueue();
        String pendingId = queue.enqueue(AssetManager.pack(snapshot));
        if (queue.count() != 1 || queue.listReports("").size() != 1
                || !queue.listReports("").getFirst().isPending()) {
            throw new AssertionError("El reporte no quedó disponible localmente como pendiente");
        }
        VersionSummary pending = queue.listVersions(snapshot.getReportId()).getFirst();
        if (!pending.isPending() || !pendingId.equals(pending.getLocalId())) {
            throw new AssertionError("La versión local no está marcada como pendiente");
        }
        ReportSnapshot loadedPending = queue.load(pending.getLocalId());
        if (!Files.isRegularFile(Path.of(loadedPending.getReport()
                .getCategoriasFotograficas().getFirst().getFotografias().getFirst().getRuta()))) {
            throw new AssertionError("La evidencia local no se pudo recuperar");
        }

        ServerStorage storage = new ServerStorage(directory.resolve("servidor"));
        try (LocalReportServer server = new LocalReportServer(0, storage)) {
            server.start();
            HttpReportClient client = new HttpReportClient(
                    "http://127.0.0.1:" + server.getPort());
            queue.flush(client);
            if (queue.count() != 0 || client.listReports("").size() != 1) {
                throw new AssertionError("El reporte pendiente no se sincronizó automáticamente");
            }
        }

        ReportSummary localSummary = queue.listReports("").getFirst();
        VersionSummary localVersion = queue.listVersions(snapshot.getReportId()).getFirst();
        if (localSummary.isPending() || localVersion.isPending()
                || localVersion.getVersion() != 1) {
            throw new AssertionError("La copia local sincronizada no se conservó correctamente");
        }
        ReportSnapshot loadedSynced = queue.load(localVersion.getLocalId());
        if (!"Cliente local".equals(loadedSynced.getReport().getCliente())) {
            throw new AssertionError("No fue posible abrir la copia local sincronizada");
        }
        System.out.println("OFFLINE_PERSISTENCE_OK");
    }
}
