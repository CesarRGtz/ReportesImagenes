package com.teosa.app.prototipo;

import java.util.UUID;

public class SubtituloFotografico {
    private String id = UUID.randomUUID().toString();
    private String titulo;
    private boolean saltoPaginaDespues;
    private String backgroundColor = "#ffffff";
    public boolean isSaltoPaginaDespues() { return saltoPaginaDespues; }
    public void setSaltoPaginaDespues(boolean value) { saltoPaginaDespues = value; }
    public String getBackgroundColor() {
        return backgroundColor != null && backgroundColor.matches("#[0-9a-fA-F]{6}")
                ? backgroundColor : "#ffffff";
    }
    public void setBackgroundColor(String value) { backgroundColor = value; }
    public static boolean saltoEntre(java.util.List<SubtituloFotografico> subtitulos,
                                     CategoriaFotografica anterior, CategoriaFotografica actual) {
        return !java.util.Objects.equals(anterior.getSubtituloId(), actual.getSubtituloId())
                && subtitulos.stream().anyMatch(s -> s.getId().equals(anterior.getSubtituloId())
                        && s.isSaltoPaginaDespues());
    }
    public SubtituloFotografico(String titulo) { this.titulo = titulo; }
    public String getId() { return id; }
    public String getTitulo() { return titulo == null ? "" : titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
}
