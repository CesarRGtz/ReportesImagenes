package com.teosa.app.prototipo;

import com.teosa.app.prototipo.domain.CategoriaFotografica;
import com.teosa.app.prototipo.domain.FirmasReporte;
import com.teosa.app.prototipo.domain.FotoEvidencia;
import com.teosa.app.prototipo.infrastructure.pdf.PdfReportGenerator;
import com.teosa.app.prototipo.domain.ReporteServicio;
import com.teosa.app.prototipo.infrastructure.persistence.JsonSupport;
import com.teosa.app.prototipo.domain.TemplateDefinition;

import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import java.nio.file.*;
import java.util.Base64;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;

public class SignatureSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of("tmp/firmas"); Files.createDirectories(dir);
        ReporteServicio report = new ReporteServicio("Cliente de prueba", "12/09/2026", "", "", "", "", "Equipo", "Trabajo");
        FirmasReporte firmas = report.getFirmas();
        firmas.setNombreElaboro("NOMBRE DEL SUPERVISOR");
        firmas.setNombreVerifico("NOMBRE DEL RESPONSABLE");
        for (int i = 0; i < 2; i++) {
            BufferedImage img = new BufferedImage(500, 180, BufferedImage.TYPE_INT_ARGB);
            var g = img.createGraphics(); g.setColor(i == 0 ? java.awt.Color.BLUE : java.awt.Color.RED);
            g.setStroke(new java.awt.BasicStroke(3));
            for (int x = 20; x < 470; x++) g.drawLine(x, 90 + (int)(35 * Math.sin(x / 22.0)),
                    x + 1, 90 + (int)(35 * Math.sin((x + 1) / 22.0)));
            g.dispose();
            var bytes = new java.io.ByteArrayOutputStream(); ImageIO.write(img, "png", bytes);
            String encoded = Base64.getEncoder().encodeToString(bytes.toByteArray());
            if (i == 0) firmas.setImagenElaboro(encoded); else firmas.setImagenVerifico(encoded);
        }
        String json = JsonSupport.GSON.toJson(report);
        report = JsonSupport.GSON.fromJson(json, ReporteServicio.class);
        if (!report.getFirmas().getImagenElaboro().equals(firmas.getImagenElaboro())
                || !report.getFirmas().getNombreVerifico().equals(firmas.getNombreVerifico()))
            throw new AssertionError("Firmas perdidas al guardar");
        for (boolean full : new boolean[]{false, true}) {
            TemplateDefinition template = TemplateDefinition.defaults();
            if (full) {
                template.getFields().clear();
                Path photo = dir.resolve("foto.png");
                ImageIO.write(new BufferedImage(400, 20, BufferedImage.TYPE_INT_RGB), "png", photo.toFile());
                CategoriaFotografica c = new CategoriaFotografica("Subtítulo");
                c.agregarFotografia(new FotoEvidencia(photo.toAbsolutePath().toString(), "Evidencia"));
                report.agregarCategoriaFotografica(c);
            }
            Path pdf = dir.resolve(full ? "firmas-pagina-final.pdf" : "firmas-contenido.pdf");
            PdfReportGenerator.generar(pdf.toFile(), report, template);
            try (var doc = Loader.loadPDF(pdf.toFile())) {
                PDFTextStripper text = new PDFTextStripper();
                text.setStartPage(doc.getNumberOfPages());
                String last = text.getText(doc);
                for (String token : new String[]{"Elaboró", "Verificó", "Supervisor", "Responsable", "NOMBRE DEL SUPERVISOR", "NOMBRE DEL RESPONSABLE"})
                    if (!last.contains(token)) throw new AssertionError("Cuadro dividido o texto ausente: " + token);
                var img = new PDFRenderer(doc).renderImageWithDPI(doc.getNumberOfPages() - 1, 100);
                ImageIO.write(img, "png", dir.resolve(full ? "final.png" : "contenido.png").toFile());
                int blue=0, red=0;
                for(int y=0;y<img.getHeight();y++) for(int x=0;x<img.getWidth();x++) {
                    var color = new java.awt.Color(img.getRGB(x,y));
                    if(color.getBlue()>180 && color.getRed()<90) blue++;
                    if(color.getRed()>180 && color.getBlue()<90) red++;
                }
                if(blue<30 || red<30) throw new AssertionError("No se dibujaron ambas firmas");
                if (!full && doc.getNumberOfPages()!=1) throw new AssertionError("Salto innecesario");
                if (full && doc.getNumberOfPages()!=1) throw new AssertionError("Las firmas que caben no deben salir del punto 3");
            }
            if (full) {
                report.getCategoriasFotograficas().get(0).setSaltoPaginaDespues(true);
                Path salto = dir.resolve("salto-firmas.pdf");
                PdfReportGenerator.generar(salto.toFile(), report, template);
                try (var doc = Loader.loadPDF(salto.toFile())) {
                    if (doc.getNumberOfPages()!=2) throw new AssertionError("No se respetó el salto anterior");
                    PDFTextStripper text = new PDFTextStripper(); text.setStartPage(2);
                    if (!text.getText(doc).contains("NOMBRE DEL SUPERVISOR")) throw new AssertionError("Faltan firmas después del salto");
                    ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(1,100), "png", dir.resolve("salto.png").toFile());
                }
            }
        }
        if (JsonSupport.GSON.fromJson("{}", ReporteServicio.class).getFirmas().tieneContenido())
            throw new AssertionError("Reporte antiguo incompatible");
        System.out.println("FIRMAS_OK");
    }
}
