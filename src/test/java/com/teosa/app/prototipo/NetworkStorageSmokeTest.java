package com.teosa.app.prototipo;

import com.teosa.app.prototipo.CategoriaFotografica;
import com.teosa.app.prototipo.FotoEvidencia;
import com.teosa.app.prototipo.ReporteServicio;
import com.teosa.app.prototipo.data.AssetManager;
import com.teosa.app.prototipo.data.ReportSnapshot;
import com.teosa.app.prototipo.data.ReportTransfer;
import com.teosa.app.prototipo.data.TemplateDefinition;
import com.teosa.app.prototipo.network.HttpReportClient;
import com.teosa.app.prototipo.network.LocalReportServer;
import com.teosa.app.prototipo.network.ServerStorage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

public class NetworkStorageSmokeTest {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("teosa-network-test-");
        System.setProperty("teosa.data.dir", temp.toString());
        Path image = temp.resolve("test.png");
        BufferedImage source = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        var graphics = source.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 320, 180);
        graphics.dispose();
        ImageIO.write(source, "png", image.toFile());

        ServerStorage storage = new ServerStorage(temp.resolve("server"));
        try (LocalReportServer server = new LocalReportServer(0, storage)) {
            server.start();
            HttpReportClient client = new HttpReportClient("http://127.0.0.1:" + server.getPort());
            if (!client.health()) throw new AssertionError("health");

            ReporteServicio report = new ReporteServicio("Cliente demo", "31/08/2026",
                    "Taller", "R-1", "C-1", "F-1", "Motor", "Prueba");
            CategoriaFotografica category = new CategoriaFotografica("Antes");
            category.getFotografias().add(new FotoEvidencia(image.toString(), "Detalle"));
            report.agregarCategoriaFotografica(category);

            ReportSnapshot snapshot = new ReportSnapshot();
            snapshot.setReportId("report-test-1234");
            snapshot.setAuthor("operador");
            snapshot.setComputer("pc-prueba");
            snapshot.setReport(report);
            snapshot.setTemplate(TemplateDefinition.defaults());
            ReportTransfer transfer = AssetManager.pack(snapshot);
            var first = client.saveReport(transfer);
            var second = client.saveReport(transfer);
            if (first.getVersion() != 1 || second.getVersion() != 2) throw new AssertionError("versions");
            if (client.listReports("cliente demo").size() != 1) throw new AssertionError("search");
            if (client.listVersions(first.getReportId()).size() != 2) throw new AssertionError("history");
            ReportTransfer loaded = client.loadReport(first.getReportId(), 1);
            if (loaded.getAssets().size() != 1) throw new AssertionError("assets");

            TemplateDefinition template = TemplateDefinition.defaults();
            template.setName("Empresa demo");
            client.saveTemplate(template);
            if (client.listTemplates().stream().noneMatch(t -> t.getName().equals("Empresa demo"))) {
                throw new AssertionError("template");
            }
            client.deleteVersion(first.getReportId(), 1);
            if (client.listVersions(first.getReportId()).size() != 1) throw new AssertionError("delete version");
            client.deleteReport(first.getReportId());
            if (!client.listReports("").isEmpty()) throw new AssertionError("delete report");
        }
        System.out.println("NETWORK_STORAGE_OK");
    }
}
