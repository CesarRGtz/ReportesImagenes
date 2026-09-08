package com.teosa.app.prototipo;

import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;

public class PageBorderSmokeTest {
    public static void main(String[] args) throws Exception {
        try (var document = Loader.loadPDF(Path.of(args[0]).toFile())) {
            var renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                var image = renderer.renderImageWithDPI(page, 72);
                int clusters = 0;
                boolean previousLine = false;
                for (int y = 670; y < 755; y++) {
                    int darkPixels = 0;
                    for (int x = 50; x <= 562; x++) {
                        var color = new java.awt.Color(image.getRGB(x, y));
                        if (color.getRed() < 40 && color.getGreen() < 40
                                && color.getBlue() < 40) darkPixels++;
                    }
                    boolean fullLine = darkPixels > 490;
                    if (fullLine && !previousLine) clusters++;
                    previousLine = fullLine;
                }
                int expected = page == 0 ? 0 : 1;
                if (clusters != expected) throw new AssertionError("La página "
                        + (page + 1) + " tiene " + clusters
                        + " cierres inferiores; se esperaba " + expected);
            }
            System.out.println("PAGE_BORDERS_OK: " + document.getNumberOfPages());
        }
    }
}
