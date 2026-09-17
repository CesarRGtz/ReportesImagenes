package com.teosa.app.prototipo.domain;

import com.teosa.app.prototipo.domain.CustomFieldValue;
import java.util.ArrayList;
import java.util.List;

public class ReporteServicio {
    private String cliente;
    private String fecha;
    private String area;
    private String remision;
    private String cotizacion;
    private String factura;
    private String datosEquipo;
    private String descripcion;
    private List<FotoEvidencia> fotografias;
    private List<CategoriaFotografica> categoriasFotograficas;
    private List<CustomFieldValue> customFields;
    private FirmasReporte firmas = new FirmasReporte();
    public FirmasReporte getFirmas() {
        if (firmas == null) firmas = new FirmasReporte();
        return firmas;
    }
    public void setFirmas(FirmasReporte value) { firmas = value; }
    private List<SubtituloFotografico> subtitulosFotograficos = new ArrayList<>();
    public List<SubtituloFotografico> getSubtitulosFotograficos() {
        if (subtitulosFotograficos == null) subtitulosFotograficos = new ArrayList<>();
        return subtitulosFotograficos;
    }
    public String subtituloPara(CategoriaFotografica categoria) {
        return getSubtitulosFotograficos().stream()
                .filter(s -> s.getId().equals(categoria.getSubtituloId()))
                .map(SubtituloFotografico::getTitulo).findFirst().orElse("");
    }

    /** Convierte el formato anterior en una lista plana sin perder títulos ni imágenes. */
    public void simplificarSubtitulos() {
        for (SubtituloFotografico padre : getSubtitulosFotograficos()) {
            List<CategoriaFotografica> hijos = getCategoriasFotograficas().stream()
                    .filter(c -> padre.getId().equals(c.getSubtituloId())).toList();
            if (hijos.isEmpty()) {
                CategoriaFotografica vacio = new CategoriaFotografica(padre.getTitulo());
                vacio.setSaltoPaginaDespues(padre.isSaltoPaginaDespues());
                getCategoriasFotograficas().add(vacio);
            } else {
                for (CategoriaFotografica hijo : hijos) {
                    if (!padre.getTitulo().isBlank()) hijo.setTitulo(padre.getTitulo() + " / " + hijo.getTitulo());
                    hijo.setSubtituloId(null);
                }
                if (padre.isSaltoPaginaDespues()) hijos.get(hijos.size() - 1).setSaltoPaginaDespues(true);
            }
        }
        getSubtitulosFotograficos().clear();
    }

    public ReporteServicio(String cliente, String fecha, String area, String remision,
                           String cotizacion, String factura, String datosEquipo, String descripcion) {
        this.cliente = cliente;
        this.fecha = fecha;
        this.area = area;
        this.remision = remision;
        this.cotizacion = cotizacion;
        this.factura = factura;
        this.datosEquipo = datosEquipo;
        this.descripcion = descripcion;
        this.fotografias = new ArrayList<>();
        this.categoriasFotograficas = new ArrayList<>();
        this.customFields = new ArrayList<>();
    }

    public void agregarFotografia(String ruta, String etiqueta, double ancho) {
        FotoEvidencia foto = new FotoEvidencia(ruta, etiqueta);
        foto.setAncho(ancho);
        this.fotografias.add(foto);
    }

    public void agregarFotografia(String ruta, String etiqueta) {
        agregarFotografia(ruta, etiqueta, 400.0);
    }

    public void agregarCategoriaFotografica(CategoriaFotografica categoria) {
        this.categoriasFotograficas.add(categoria);
    }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getRemision() { return remision; }
    public void setRemision(String remision) { this.remision = remision; }

    public String getCotizacion() { return cotizacion; }
    public void setCotizacion(String cotizacion) { this.cotizacion = cotizacion; }

    public String getFactura() { return factura; }
    public void setFactura(String factura) { this.factura = factura; }

    public String getDatosEquipo() { return datosEquipo; }
    public void setDatosEquipo(String datosEquipo) { this.datosEquipo = datosEquipo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public List<FotoEvidencia> getFotografias() {
        if (fotografias == null) fotografias = new ArrayList<>();
        return fotografias;
    }
    public void setFotografias(List<FotoEvidencia> fotografias) { this.fotografias = fotografias; }

    public List<CategoriaFotografica> getCategoriasFotograficas() {
        if (categoriasFotograficas == null) categoriasFotograficas = new ArrayList<>();
        return categoriasFotograficas;
    }
    public void setCategoriasFotograficas(List<CategoriaFotografica> categoriasFotograficas) {
        this.categoriasFotograficas = categoriasFotograficas == null
                ? new ArrayList<>() : categoriasFotograficas;
    }

    public List<CustomFieldValue> getCustomFields() {
        if (customFields == null) customFields = new ArrayList<>();
        return customFields;
    }

    public void setCustomFields(List<CustomFieldValue> customFields) {
        this.customFields = customFields == null ? new ArrayList<>() : customFields;
    }
}
