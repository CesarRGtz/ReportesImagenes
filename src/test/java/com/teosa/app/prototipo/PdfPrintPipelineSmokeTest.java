package com.teosa.app.prototipo;

import com.teosa.app.prototipo.data.TemplateDefinition;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class PdfPrintPipelineSmokeTest {
    public static void main(String[] args) throws Exception {
        Path pdf = Files.createTempFile("reporte-teosa-print-test-", ".pdf");
        Path categoryBreakPdf = Files.createTempFile("reporte-teosa-category-break-test-", ".pdf");
        Path image = Files.createTempFile("reporte-teosa-photo-test-", ".png");
        Path fontCache = Files.createTempDirectory("reporte-teosa-font-cache-");
        try {
            System.setProperty("pdfbox.fontcache", fontCache.toString());
            BufferedImage sampleImage = new BufferedImage(640, 420, BufferedImage.TYPE_INT_RGB);
            var graphics = sampleImage.createGraphics();
            graphics.setColor(new Color(78, 121, 167));
            graphics.fillRect(0, 0, sampleImage.getWidth(), sampleImage.getHeight());
            graphics.dispose();
            ImageIO.write(sampleImage, "png", image.toFile());

            ReporteServicio report = new ReporteServicio(
                    "Cliente de prueba", "02/09/2026", "Taller", "R-1",
                    "C-1", "F-1", "Motor", "Prueba de impresión");
            CategoriaFotografica category = new CategoriaFotografica("Categoría de prueba");
            for (int index = 0; index < 10; index++) {
                FotoEvidencia photo = new FotoEvidencia(image.toString(),
                        "Comentario de prueba para la imagen " + (index + 1));
                photo.setAncho(230);
                category.agregarFotografia(photo);
            }
            report.agregarCategoriaFotografica(category);

            TemplateDefinition template = TemplateDefinition.defaults();
            template.setSection3Title("3. SECCION FOTOS PRUEBA");
            template.setStartPhotosOnNewPage(true);
            PdfReportGenerator.generar(pdf.toFile(), report, template);

            try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
                if (document.getNumberOfPages() < 2) {
                    throw new AssertionError("El salto antes del punto 3 no creó una página nueva");
                }
                PDFTextStripper firstPageStripper = new PDFTextStripper();
                firstPageStripper.setStartPage(1);
                firstPageStripper.setEndPage(1);
                String firstPageText = firstPageStripper.getText(document).toUpperCase(Locale.ROOT);
                if (firstPageText.contains("SECCION FOTOS PRUEBA")) {
                    throw new AssertionError("El punto 3 apareció antes del salto de página");
                }
                String allText = new PDFTextStripper().getText(document).toUpperCase(Locale.ROOT);
                if (!allText.contains("SECCION FOTOS PRUEBA") || allText.contains("CONTINUACI")) {
                    throw new AssertionError("Los encabezados fotográficos no respetan el formato esperado");
                }
                BufferedImage renderedPage = new PDFRenderer(document)
                        .renderImageWithDPI(0, 200, ImageType.RGB);
                if (renderedPage.getWidth() < 1 || renderedPage.getHeight() < 1) {
                    throw new AssertionError("La página del PDF no se pudo renderizar para impresión");
                }
            }

            ReporteServicio categoryBreakReport = new ReporteServicio(
                    "Cliente de prueba", "02/09/2026", "Taller", "R-1",
                    "C-1", "F-1", "Motor", "Prueba de separación entre categorías");
            CategoriaFotografica firstCategory = new CategoriaFotografica("CATEGORIA UNO");
            FotoEvidencia firstPhoto = new FotoEvidencia(image.toString(), "Primera categoría");
            firstPhoto.setAncho(100);
            firstCategory.agregarFotografia(firstPhoto);
            CategoriaFotografica secondCategory = new CategoriaFotografica("CATEGORIA DOS");
            FotoEvidencia secondPhoto = new FotoEvidencia(image.toString(), "Segunda categoría");
            secondPhoto.setAncho(100);
            secondCategory.agregarFotografia(secondPhoto);
            secondCategory.setSaltoPaginaDespues(true);
            CategoriaFotografica thirdCategory = new CategoriaFotografica("CATEGORIA TRES");
            FotoEvidencia thirdPhoto = new FotoEvidencia(image.toString(), "Tercera categoría");
            thirdPhoto.setAncho(100);
            thirdCategory.agregarFotografia(thirdPhoto);
            categoryBreakReport.agregarCategoriaFotografica(firstCategory);
            categoryBreakReport.agregarCategoriaFotografica(secondCategory);
            categoryBreakReport.agregarCategoriaFotografica(thirdCategory);

            TemplateDefinition categoryBreakTemplate = TemplateDefinition.defaults();
            categoryBreakTemplate.setStartPhotosOnNewPage(true);
            PdfReportGenerator.generar(categoryBreakPdf.toFile(), categoryBreakReport,
                    categoryBreakTemplate);
            try (PDDocument document = Loader.loadPDF(categoryBreakPdf.toFile())) {
                int firstCategoryPage = pageContaining(document, "CATEGORIA UNO");
                int secondCategoryPage = pageContaining(document, "CATEGORIA DOS");
                int thirdCategoryPage = pageContaining(document, "CATEGORIA TRES");
                if (firstCategoryPage < 1 || secondCategoryPage != firstCategoryPage
                        || thirdCategoryPage <= secondCategoryPage) {
                    throw new AssertionError(
                            "Los saltos de página individuales no se aplicaron correctamente");
                }
            }
            System.out.println("PDF_PRINT_PIPELINE_OK");
        } finally {
            Files.deleteIfExists(pdf);
            Files.deleteIfExists(categoryBreakPdf);
            Files.deleteIfExists(image);
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

    private static int pageContaining(PDDocument document, String expectedText) throws Exception {
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            String text = stripper.getText(document).toUpperCase(Locale.ROOT);
            if (text.contains(expectedText.toUpperCase(Locale.ROOT))) {
                return page;
            }
        }
        return -1;
    }
}
