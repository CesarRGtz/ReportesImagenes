package com.teosa.app.prototipo;

public class FotoEvidencia {
    private String ruta;
    private String etiqueta;
    private double ancho = 400.0; // Ancho predeterminado
    private String rutaOriginal;
    private double cropX;
    private double cropY;
    private double cropWidth = 1.0;
    private double cropHeight = 1.0;

    public FotoEvidencia(String ruta, String etiqueta) {
        this.ruta = ruta;
        this.rutaOriginal = ruta;
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

    public String getRutaOriginal() { return rutaOriginal == null ? ruta : rutaOriginal; }
    public void setRutaOriginal(String rutaOriginal) { this.rutaOriginal = rutaOriginal; }
    public double getCropX() { return cropX; }
    public void setCropX(double cropX) { this.cropX = cropX; }
    public double getCropY() { return cropY; }
    public void setCropY(double cropY) { this.cropY = cropY; }
    public double getCropWidth() { return cropWidth <= 0 ? 1.0 : cropWidth; }
    public void setCropWidth(double cropWidth) { this.cropWidth = cropWidth; }
    public double getCropHeight() { return cropHeight <= 0 ? 1.0 : cropHeight; }
    public void setCropHeight(double cropHeight) { this.cropHeight = cropHeight; }
}
