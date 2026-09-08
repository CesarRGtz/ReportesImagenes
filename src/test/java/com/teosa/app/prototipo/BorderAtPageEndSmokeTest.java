package com.teosa.app.prototipo;

import com.teosa.app.prototipo.data.TemplateDefinition;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;

public class BorderAtPageEndSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of("tmp", "pdfs", "borde-extremo").toAbsolutePath();
        Files.createDirectories(directory);
        Path imagePath = directory.resolve("foto-alta.png");
        BufferedImage image = new BufferedImage(496, 599, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(79, 112, 154));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ImageIO.write(image, "png", imagePath.toFile());

        ReporteServicio report = new ReporteServicio("Cliente", "07/09/2026",
                "Área", "R-1", "C-1", "F-1", "Equipo", "Trabajo realizado");
        CategoriaFotografica category = new CategoriaFotografica("Categoría de prueba");
        FotoEvidencia photo = new FotoEvidencia(imagePath.toString(), "");
        photo.setAncho(ReportLayout.MAX_PHOTO_WIDTH);
        category.agregarFotografia(photo);
        report.agregarCategoriaFotografica(category);

        TemplateDefinition template = TemplateDefinition.defaults();
        template.setStartPhotosOnNewPage(true);
        Path pdf = directory.resolve("borde-al-extremo.pdf");
        PdfReportGenerator.generar(pdf.toFile(), report, template);
        try (var document = Loader.loadPDF(pdf.toFile())) {
            if (document.getNumberOfPages() != 2) {
                throw new AssertionError("La fotografía alta generó una página en blanco: "
                        + document.getNumberOfPages() + " páginas");
            }
        }
        PageBorderSmokeTest.main(new String[] {pdf.toString()});
        System.out.println(pdf);
    }
}
