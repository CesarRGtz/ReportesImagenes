package com.teosa.app.prototipo;

public class FotoEvidencia {
    private String ruta;
    private String etiqueta;
    private double ancho = 400.0; // Ancho predeterminado

    public FotoEvidencia(String ruta, String etiqueta) {
        this.ruta = ruta;
        this.etiqueta = etiqueta;
    }

    public String getRuta() {
        return ruta;
    }

    public void setRuta(String ruta) {
        this.ruta = ruta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public void setEtiqueta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public double getAncho() {
        return ancho;
    }

    public void setAncho(double ancho) {
        this.ancho = ancho;
    }
}