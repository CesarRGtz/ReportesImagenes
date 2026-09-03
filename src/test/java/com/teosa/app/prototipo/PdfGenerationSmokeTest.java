package com.teosa.app.prototipo;

import com.teosa.app.prototipo.CategoriaFotografica;
import com.teosa.app.prototipo.FotoEvidencia;
import com.teosa.app.prototipo.PdfReportGenerator;
import com.teosa.app.prototipo.ReporteServicio;
import com.teosa.app.prototipo.data.CustomFieldValue;
import com.teosa.app.prototipo.data.FieldDefinition;
import com.teosa.app.prototipo.data.HeaderLine;
import com.teosa.app.prototipo.data.TemplateDefinition;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class PdfGenerationSmokeTest {
    public static void main(String[] args) throws Exception {
        Path temp = Path.of("tmp", "pdfs", "teosa-smoke").toAbsolutePath();
        Path output = Path.of("output", "pdf", "reporte-prueba-integracion.pdf").toAbsolutePath();
        Files.createDirectories(temp);
        Files.createDirectories(output.getParent());

        ReporteServicio report = new ReporteServicio("Cargill de México", "31/08/2026",
                "Motores", "REM-104", "COT-889", "FAC-42",
                "Motor ABB de 560 kW, serie 12345. Inspección general y diagnóstico.",
                "Se realizó desmontaje, limpieza, revisión eléctrica, cambio de componentes "
                        + "y pruebas finales de funcionamiento.");
        report.getCustomFields().add(new CustomFieldValue("orden", "OT-2026-031"));

        CategoriaFotografica before = new CategoriaFotografica(
                "Recepción, inspección inicial y evidencia del estado del equipo");
        for (int index = 0; index < 7; index++) {
            Path image = temp.resolve("foto-" + index + ".png");
            int width = index % 2 == 0 ? 800 : 560;
            int height = index % 3 == 0 ? 720 : 420;
            BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var graphics = bitmap.createGraphics();
            graphics.setColor(new Color(40 + index * 20, 90 + index * 10, 150));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.WHITE);
            graphics.drawString("Fotografía de prueba " + (index + 1), 30, 40);
            graphics.dispose();
            ImageIO.write(bitmap, "png", image.toFile());
            FotoEvidencia photo = new FotoEvidencia(image.toString(),
                    "Detalle individual de la fotografía " + (index + 1)
                            + ": observación técnica opcional.");
            photo.setAncho(index < 4 ? 145 : 230);
            before.getFotografias().add(photo);
        }
        report.agregarCategoriaFotografica(before);

        TemplateDefinition template = TemplateDefinition.defaults();
        template.setName("Prueba de integración");
        template.getFields().put("orden", new FieldDefinition("orden", "Orden de trabajo:", 2, true));
        template.getFields().get("remision").setOrder(3);
        template.getFields().get("cotizacion").setOrder(4);
        template.getFields().get("factura").setOrder(5);
        template.setSectionBackgroundColor("#dbeafe");
        template.getHeaderLines().clear();
        template.getHeaderLines().add(new HeaderLine("REPORTE DE SERVICIO", 16, true, false, "#1f4e79"));
        template.getHeaderLines().add(new HeaderLine("Elaborado para {empresa}", 12, true, true, "#5b7699"));
        template.getHeaderLines().add(new HeaderLine("Departamento de mantenimiento", 9, false, false, "#334155"));
        Path customLogo = temp.resolve("logo-personalizado.png");
        BufferedImage logo = new BufferedImage(500, 140, BufferedImage.TYPE_INT_RGB);
        var logoGraphics = logo.createGraphics();
        logoGraphics.setColor(new Color(235, 244, 255));
        logoGraphics.fillRect(0, 0, 500, 140);
        logoGraphics.setColor(new Color(31, 78, 121));
        logoGraphics.drawString("LOGO PERSONALIZADO", 160, 75);
        logoGraphics.dispose();
        ImageIO.write(logo, "png", customLogo.toFile());
        template.setHeaderImageBase64(Base64.getEncoder().encodeToString(
                Files.readAllBytes(customLogo)));
        template.setHeaderImageFileName(customLogo.getFileName().toString());
        template.setHeaderImageAspectRatio(500.0 / 140.0);
        template.setHeaderImageWidth(190);
        template.setHeaderGap(28);
        template.setHeaderLayout("SIDE_BY_SIDE");
        template.setHeaderTextAlignment("CENTER");
        template.getCategoryTitleStyle().setFontSize(16);
        template.getCategoryTitleStyle().setColor("#7c2d12");
        template.setCategoryTitleAlignment("CENTER");
        PdfReportGenerator.generar(output.toFile(), report, template);
        if (!Files.isRegularFile(output) || Files.size(output) < 1000) throw new AssertionError("pdf");
        System.out.println(output);
    }
}
