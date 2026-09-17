package com.teosa.app.prototipo;

import com.teosa.app.prototipo.domain.CategoriaFotografica;
import com.teosa.app.prototipo.domain.FotoEvidencia;
import com.teosa.app.prototipo.infrastructure.pdf.PdfReportGenerator;
import com.teosa.app.prototipo.domain.ReporteServicio;
import com.teosa.app.prototipo.domain.ReportLayout;

import com.teosa.app.prototipo.domain.TemplateDefinition;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;

public class PhotoBorderModeSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of("tmp", "pdfs", "modo-borde").toAbsolutePath();
        Files.createDirectories(directory);
        Path imagePath = directory.resolve("foto-horizontal.png");
        BufferedImage image = new BufferedImage(1000, 250, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(79, 112, 154));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ImageIO.write(image, "png", imagePath.toFile());

        int contentBottom = generateAndFindBottom(directory.resolve("contenido.pdf"),
                imagePath, TemplateDefinition.PHOTO_BORDER_CONTENT);
        int fullPageBottom = generateAndFindBottom(directory.resolve("pagina-completa.pdf"),
                imagePath, TemplateDefinition.PHOTO_BORDER_FULL_PAGE);

        if (contentBottom >= 730) {
            throw new AssertionError("El borde ajustado terminó en el pie de página: y=" + contentBottom);
        }
        if (fullPageBottom < 740) {
            throw new AssertionError("El borde de página completa no llegó al pie: y=" + fullPageBottom);
        }
        if (contentBottom >= fullPageBottom) {
            throw new AssertionError("Los dos modos de borde produjeron el mismo cierre");
        }

        Path portraitPath = directory.resolve("foto-vertical.png");
        BufferedImage portrait = new BufferedImage(500, 1200, BufferedImage.TYPE_INT_RGB);
        var portraitGraphics = portrait.createGraphics();
        portraitGraphics.setColor(new Color(79, 112, 154));
        portraitGraphics.fillRect(0, 0, portrait.getWidth(), portrait.getHeight());
        portraitGraphics.dispose();
        ImageIO.write(portrait, "png", portraitPath.toFile());
        Path firstPagePdf = directory.resolve("ajuste-primera-pagina.pdf");
        generate(firstPagePdf, portraitPath, TemplateDefinition.PHOTO_BORDER_CONTENT);
        try (var document = Loader.loadPDF(firstPagePdf.toFile())) {
            if (document.getNumberOfPages() != 1) {
                throw new AssertionError("La imagen al 100 % no se ajustó al espacio de la primera página");
            }
        }
        System.out.println("PHOTO_BORDER_MODES_OK: contenido=" + contentBottom
                + ", pagina=" + fullPageBottom);
    }

    private static int generateAndFindBottom(Path pdf, Path imagePath, String mode) throws Exception {
        generate(pdf, imagePath, mode);

        try (var document = Loader.loadPDF(pdf.toFile())) {
            if (document.getNumberOfPages() != 1) {
                throw new AssertionError("La prueba debe caber en la primera página");
            }
            var rendered = new PDFRenderer(document).renderImageWithDPI(0, 72);
            int lastLine = -1;
            for (int y = 250; y < 760; y++) {
                int darkPixels = 0;
                for (int x = 50; x <= 562; x++) {
                    Color pixel = new Color(rendered.getRGB(x, y));
                    if (pixel.getRed() < 40 && pixel.getGreen() < 40 && pixel.getBlue() < 40) {
                        darkPixels++;
                    }
                }
                if (darkPixels > 490) lastLine = y;
            }
            if (lastLine < 0) throw new AssertionError("No se encontró el cierre inferior del borde");
            return lastLine;
        }
    }

    private static void generate(Path pdf, Path imagePath, String mode) throws Exception {
        ReporteServicio report = new ReporteServicio("Cliente", "08/09/2026",
                "Área", "R-1", "C-1", "F-1", "Equipo", "Trabajo realizado");
        CategoriaFotografica category = new CategoriaFotografica("Categoría de prueba");
        FotoEvidencia photo = new FotoEvidencia(imagePath.toString(), "");
        photo.setAncho(ReportLayout.MAX_PHOTO_WIDTH);
        category.agregarFotografia(photo);
        report.agregarCategoriaFotografica(category);

        TemplateDefinition template = TemplateDefinition.defaults();
        template.setPhotoBorderMode(mode);
        PdfReportGenerator.generar(pdf.toFile(), report, template);
    }
}
