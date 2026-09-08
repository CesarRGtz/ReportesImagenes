package com.teosa.app.prototipo;

public class PhotoScaleSmokeTest {
    public static void main(String[] args) {
        double fullWidth = ReportLayout.MAX_PHOTO_WIDTH;

        double[] portrait100 = ReportLayout.scaleImage(
                1000, 2000, fullWidth, fullWidth, 200);
        double[] portrait50 = ReportLayout.scaleImage(
                1000, 2000, fullWidth / 2.0, fullWidth / 2.0, 200);

        assertClose(100, portrait100[0], "ancho vertical al 100 %");
        assertClose(200, portrait100[1], "alto vertical al 100 %");
        assertClose(50, portrait50[0], "ancho vertical al 50 %");
        assertClose(100, portrait50[1], "alto vertical al 50 %");

        double[] landscape100 = ReportLayout.scaleImage(
                1000, 500, fullWidth, fullWidth, 600);
        double[] landscape50 = ReportLayout.scaleImage(
                1000, 500, fullWidth / 2.0, fullWidth / 2.0, 600);
        assertClose(fullWidth, landscape100[0], "ancho horizontal al 100 %");
        assertClose(fullWidth / 2.0, landscape50[0], "ancho horizontal al 50 %");
        assertClose(landscape100[1] / 2.0, landscape50[1], "alto horizontal al 50 %");

        System.out.println("PHOTO_SCALE_OK");
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 0.01) {
            throw new AssertionError(label + ": se esperaba " + expected + " y se obtuvo " + actual);
        }
    }
}
