package com.teosa.app.prototipo.domain;

/** Imágenes incluidas en el reporte para poder abrirlo también sin conexión. */
public class FirmasReporte {
    private String nombreElaboro = "";
    private String nombreVerifico = "";
    private String imagenElaboro = "";
    private String imagenVerifico = "";
    public String getNombreElaboro() { return nombreElaboro == null ? "" : nombreElaboro; }
    public void setNombreElaboro(String value) { nombreElaboro = value; }
    public String getNombreVerifico() { return nombreVerifico == null ? "" : nombreVerifico; }
    public void setNombreVerifico(String value) { nombreVerifico = value; }
    public String getImagenElaboro() { return imagenElaboro == null ? "" : imagenElaboro; }
    public void setImagenElaboro(String value) { imagenElaboro = value; }
    public String getImagenVerifico() { return imagenVerifico == null ? "" : imagenVerifico; }
    public void setImagenVerifico(String value) { imagenVerifico = value; }
    public boolean tieneContenido() {
        return !getNombreElaboro().isBlank() || !getNombreVerifico().isBlank()
                || !getImagenElaboro().isBlank() || !getImagenVerifico().isBlank();
    }
}
