package com.teosa.app.prototipo;

import java.util.ArrayList;
import java.util.List;

public class CategoriaFotografica {
    private String titulo;
    private boolean saltoPaginaDespues;
    private List<FotoEvidencia> fotografias;

    public CategoriaFotografica(String titulo) {
        this.titulo = titulo;
        this.fotografias = new ArrayList<>();
    }

    public void agregarFotografia(FotoEvidencia fotografia) {
        fotografias.add(fotografia);
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public boolean isSaltoPaginaDespues() {
        return saltoPaginaDespues;
    }

    public void setSaltoPaginaDespues(boolean saltoPaginaDespues) {
        this.saltoPaginaDespues = saltoPaginaDespues;
    }

    public List<FotoEvidencia> getFotografias() {
        return fotografias;
    }

    public void setFotografias(List<FotoEvidencia> fotografias) {
        this.fotografias = fotografias == null ? new ArrayList<>() : fotografias;
    }
}
