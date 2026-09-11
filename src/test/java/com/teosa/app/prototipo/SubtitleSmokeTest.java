package com.teosa.app.prototipo;

import com.teosa.app.prototipo.data.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

public class SubtitleSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of("tmp/subtitulos"); Files.createDirectories(dir);
        Path image = dir.resolve("foto.png");
        ImageIO.write(new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB), "png", image.toFile());
        ReporteServicio r = new ReporteServicio("Cliente", "08/09/2026", "", "", "", "", "Equipo", "Trabajo");
        for (int i = 1; i <= 2; i++) {
            SubtituloFotografico s = new SubtituloFotografico("SUBTITULO " + i);
            s.setSaltoPaginaDespues(i == 1);
            s.setBackgroundColor("#ffcc00");
            r.getSubtitulosFotograficos().add(s);
            CategoriaFotografica c = new CategoriaFotografica("CATEGORIA " + i);
            c.setSubtituloId(s.getId());
            c.agregarFotografia(new FotoEvidencia(image.toAbsolutePath().toString(), "IMAGEN " + i));
            r.agregarCategoriaFotografica(c);
        }
        r = JsonSupport.GSON.fromJson(JsonSupport.GSON.toJson(r), ReporteServicio.class);
        if (!r.getSubtitulosFotograficos().get(0).isSaltoPaginaDespues()
                || !r.getSubtitulosFotograficos().get(0).getBackgroundColor().equals("#ffcc00"))
            throw new AssertionError("Se perdieron los ajustes del subtítulo");
        if (!SubtituloFotografico.saltoEntre(r.getSubtitulosFotograficos(),
                r.getCategoriasFotograficas().get(0), r.getCategoriasFotograficas().get(1)))
            throw new AssertionError("Falta salto entre subtítulos");
        if (SubtituloFotografico.saltoEntre(r.getSubtitulosFotograficos(),
                r.getCategoriasFotograficas().get(0), r.getCategoriasFotograficas().get(0)))
            throw new AssertionError("Salto dentro del mismo subtítulo");
        if (!r.subtituloPara(r.getCategoriasFotograficas().get(1)).equals("SUBTITULO 2"))
            throw new AssertionError("Se perdió la asociación al guardar");
        Path pdf = dir.resolve("subtitulos.pdf");
        r.simplificarSubtitulos();
        if (!r.getSubtitulosFotograficos().isEmpty()
                || r.getCategoriasFotograficas().size() != 2
                || !r.getCategoriasFotograficas().get(0).isSaltoPaginaDespues())
            throw new AssertionError("Migración incompleta");
        String migrated = JsonSupport.GSON.toJson(r);
        r.simplificarSubtitulos();
        if (!migrated.equals(JsonSupport.GSON.toJson(r))) throw new AssertionError("Migración no idempotente");
        r = JsonSupport.GSON.fromJson(migrated, ReporteServicio.class);
        if (r.getCategoriasFotograficas().stream().mapToInt(c -> c.getFotografias().size()).sum() != 2)
            throw new AssertionError("Se perdieron imágenes");
        TemplateDefinition template = TemplateDefinition.defaults();
        template.setPhotoSubtitleBackgroundColor("#ffcc00");
        PdfReportGenerator.generar(pdf.toFile(), r, template);
        try (var doc = Loader.loadPDF(pdf.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            PDFTextStripper firstPage = new PDFTextStripper();
            firstPage.setStartPage(1); firstPage.setEndPage(1);
            if (firstPage.getText(doc).contains("SUBTITULO 2"))
                throw new AssertionError("El segundo subtítulo no cambió de página");
            var rendered = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0, 72);
            ImageIO.write(rendered, "png", dir.resolve("plano-1.png").toFile());
            ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(1, 72),
                    "png", dir.resolve("plano-2.png").toFile());
            if (!text.contains("SUBTITULO 1 / CATEGORIA 1"))
                throw new AssertionError("Los títulos no quedaron en un solo nivel");
            int colored = 0;
            for (int y = 0; y < rendered.getHeight(); y++)
                for (int x = 50; x < 562; x++) {
                    java.awt.Color pixel = new java.awt.Color(rendered.getRGB(x, y));
                    if (pixel.getRed() > 240 && pixel.getGreen() > 190
                            && pixel.getGreen() < 220 && pixel.getBlue() < 20) colored++;
                }
            if (colored < 1000) throw new AssertionError("El fondo no aparece en el PDF");
            int previous = -1;
            for (String token : new String[]{"SUBTITULO 1", "CATEGORIA 1", "IMAGEN 1", "SUBTITULO 2", "CATEGORIA 2", "IMAGEN 2"}) {
                int position = text.indexOf(token);
                if (position <= previous) throw new AssertionError("Orden incorrecto: " + token);
                previous = position;
            }
        }
        ReporteServicio legacy = JsonSupport.GSON.fromJson("{}", ReporteServicio.class);
        if (!legacy.getSubtitulosFotograficos().isEmpty()) throw new AssertionError("Compatibilidad");
        System.out.println("SUBTITULOS_OK");
    }
}
