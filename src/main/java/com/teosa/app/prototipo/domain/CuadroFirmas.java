package com.teosa.app.prototipo.domain;
public final class CuadroFirmas {
    public static final double MARGEN = 72.0 / 2.54; // Un centímetro en puntos.
    public static final double ESPACIO = MARGEN * 2;
    public static final double ANCHO = ReportLayout.CONTENT_WIDTH - MARGEN * 2;
    public static double altoNombre(FirmasReporte f) {
        return Math.max(22, Math.max(ReportLayout.estimateDescriptionHeight(f.getNombreElaboro(), ANCHO / 2 - 8, 11),
                ReportLayout.estimateDescriptionHeight(f.getNombreVerifico(), ANCHO / 2 - 8, 11)) + 6);
    }
    public static double alto(FirmasReporte f) { return 112 + 22 + 32 + altoNombre(f); }
}
