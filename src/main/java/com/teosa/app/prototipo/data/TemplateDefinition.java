package com.teosa.app.prototipo.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TemplateDefinition {
    private String name = "Formato predeterminado";
    private long lastUsedAt;
    private Map<String, FieldDefinition> fields = new LinkedHashMap<>();
    private List<HeaderLine> headerLines = new ArrayList<>();
    private TextStyle photoCommentStyle = new TextStyle();
    private TextStyle categoryTitleStyle = categoryDefaults();
    private String categoryTitleAlignment = "LEFT";
    private String headerImageFileName = "Imagen12.jpg";
    private String headerImageBase64 = "";
    private double headerImageWidth = 135;
    private double headerImageAspectRatio = 135.0 / 87.0;
    private double headerGap = 18;
    private String headerLayout = "SIDE_BY_SIDE";
    private String headerTextAlignment = "CENTER";
    private String sectionBackgroundColor = "#bfbfbf";
    private String section1Title = "1.  DATOS DEL EQUIPO:";
    private String section2Title = "2.  DESCRIPCIÓN DEL TRABAJO:";
    private String section3Title = "3.  REPORTE FOTOGRÁFICO DEL ANTES, DURANTE Y DESPUÉS DE REALIZAR EL TRABAJO:";
    private Map<String, String> presetValues = new LinkedHashMap<>();

    public static TemplateDefinition defaults() {
        TemplateDefinition result = new TemplateDefinition();
        result.fields.put("fecha", new FieldDefinition("fecha", "Fecha de Recepción de Equipo:", 0, false));
        result.fields.put("area", new FieldDefinition("area", "Área:", 1, false));
        result.fields.put("remision", new FieldDefinition("remision", "Remisión:", 2, false));
        result.fields.put("cotizacion", new FieldDefinition("cotizacion", "Cotización:", 3, false));
        result.fields.put("factura", new FieldDefinition("factura", "Factura:", 4, false));
        result.headerLines.add(new HeaderLine("Reporte de Servicio Elaborado para", 14, true, true, "#5b7699"));
        result.headerLines.add(new HeaderLine("{empresa}", 14, true, true, "#5b7699"));
        return result;
    }

    private static TextStyle categoryDefaults() {
        TextStyle style = new TextStyle();
        style.setFontFamily("Arial");
        style.setFontSize(14);
        style.setColor("#1f4e79");
        style.setBold(true);
        style.setItalic(false);
        return style;
    }

    public List<FieldDefinition> orderedFields() {
        List<FieldDefinition> result = new ArrayList<>(getFields().values());
        result.sort(Comparator.comparingInt(FieldDefinition::getOrder));
        return result;
    }

    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name; }
    public long getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(long lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Map<String, FieldDefinition> getFields() { if (fields == null) fields = new LinkedHashMap<>(); return fields; }
    public void setFields(Map<String, FieldDefinition> fields) { this.fields = fields; }
    public List<HeaderLine> getHeaderLines() { if (headerLines == null) headerLines = new ArrayList<>(); return headerLines; }
    public void setHeaderLines(List<HeaderLine> headerLines) { this.headerLines = headerLines; }
    public TextStyle getPhotoCommentStyle() { if (photoCommentStyle == null) photoCommentStyle = new TextStyle(); return photoCommentStyle; }
    public void setPhotoCommentStyle(TextStyle photoCommentStyle) { this.photoCommentStyle = photoCommentStyle; }
    public TextStyle getCategoryTitleStyle() { if (categoryTitleStyle == null) categoryTitleStyle = categoryDefaults(); return categoryTitleStyle; }
    public void setCategoryTitleStyle(TextStyle categoryTitleStyle) { this.categoryTitleStyle = categoryTitleStyle; }
    public String getCategoryTitleAlignment() { return categoryTitleAlignment == null ? "LEFT" : categoryTitleAlignment; }
    public void setCategoryTitleAlignment(String categoryTitleAlignment) { this.categoryTitleAlignment = categoryTitleAlignment; }
    public String getHeaderImageFileName() { return headerImageFileName == null ? "Imagen12.jpg" : headerImageFileName; }
    public void setHeaderImageFileName(String headerImageFileName) { this.headerImageFileName = headerImageFileName; }
    public String getHeaderImageBase64() { return headerImageBase64 == null ? "" : headerImageBase64; }
    public void setHeaderImageBase64(String headerImageBase64) { this.headerImageBase64 = headerImageBase64; }
    public double getHeaderImageWidth() { return headerImageWidth <= 0 ? 135 : headerImageWidth; }
    public void setHeaderImageWidth(double headerImageWidth) { this.headerImageWidth = headerImageWidth; }
    public double getHeaderImageAspectRatio() { return headerImageAspectRatio <= 0 ? 135.0 / 87.0 : headerImageAspectRatio; }
    public void setHeaderImageAspectRatio(double headerImageAspectRatio) { this.headerImageAspectRatio = headerImageAspectRatio; }
    public double getHeaderGap() { return headerGap < 0 ? 18 : headerGap; }
    public void setHeaderGap(double headerGap) { this.headerGap = headerGap; }
    public String getHeaderLayout() { return headerLayout == null ? "SIDE_BY_SIDE" : headerLayout; }
    public void setHeaderLayout(String headerLayout) { this.headerLayout = headerLayout; }
    public String getHeaderTextAlignment() { return headerTextAlignment == null ? "CENTER" : headerTextAlignment; }
    public void setHeaderTextAlignment(String headerTextAlignment) { this.headerTextAlignment = headerTextAlignment; }
    public String getSectionBackgroundColor() { return sectionBackgroundColor == null ? "#bfbfbf" : sectionBackgroundColor; }
    public void setSectionBackgroundColor(String sectionBackgroundColor) { this.sectionBackgroundColor = sectionBackgroundColor; }
    public String getSection1Title() { return section1Title; }
    public void setSection1Title(String section1Title) { this.section1Title = section1Title; }
    public String getSection2Title() { return section2Title; }
    public void setSection2Title(String section2Title) { this.section2Title = section2Title; }
    public String getSection3Title() { return section3Title; }
    public void setSection3Title(String section3Title) { this.section3Title = section3Title; }
    public Map<String, String> getPresetValues() { if (presetValues == null) presetValues = new LinkedHashMap<>(); return presetValues; }
    public void setPresetValues(Map<String, String> presetValues) { this.presetValues = presetValues; }
}
