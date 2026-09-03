package com.teosa.app.prototipo;

import com.teosa.app.prototipo.data.TemplateDefinition;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

public class PdfPrintPipelineSmokeTest {
    public static void main(String[] args) throws Exception {
        Path pdf = Files.createTempFile("reporte-teosa-print-test-", ".pdf");
        Path fontCache = Files.createTempDirectory("reporte-teosa-font-cache-");
        try {
            System.setProperty("pdfbox.fontcache", fontCache.toString());
            ReporteServicio report = new ReporteServicio(
                    "Cliente de prueba", "02/09/2026", "Taller", "R-1",
                    "C-1", "F-1", "Motor", "Prueba de impresión");
            PdfReportGenerator.generar(pdf.toFile(), report, TemplateDefinition.defaults());

            try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
                if (document.getNumberOfPages() < 1) {
                    throw new AssertionError("El PDF generado no contiene páginas imprimibles");
                }
                BufferedImage renderedPage = new PDFRenderer(document)
                        .renderImageWithDPI(0, 200, ImageType.RGB);
                if (renderedPage.getWidth() < 1 || renderedPage.getHeight() < 1) {
                    throw new AssertionError("La página del PDF no se pudo renderizar para impresión");
                }
            }
            System.out.println("PDF_PRINT_PIPELINE_OK");
        } finally {
            Files.deleteIfExists(pdf);
            try (var files = Files.list(fontCache)) {
                files.forEach(file -> {
                    try {
                        Files.deleteIfExists(file);
                    } catch (Exception ignored) {
                        // La prueba continuará limpiando el directorio aunque un archivo ya no exista.
                    }
                });
            }
            Files.deleteIfExists(fontCache);
        }
    }
}
