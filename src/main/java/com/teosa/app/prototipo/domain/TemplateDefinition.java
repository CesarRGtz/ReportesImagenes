package com.teosa.app.prototipo.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TemplateDefinition {
    public static final String PHOTO_BORDER_FULL_PAGE = "FULL_PAGE";
    public static final String PHOTO_BORDER_CONTENT = "CONTENT";

    private DocumentKind kind;
    public DocumentKind getKind(){return kind==null?DocumentKind.REPORT:kind;}
    public String storageName(){return getKind()==DocumentKind.QUOTATION?"quotation:"+getName():getName();}
    public static TemplateDefinition quotationDefaults(){
        TemplateDefinition t=new TemplateDefinition();t.kind=DocumentKind.QUOTATION;
        t.name="Cotización predeterminada";
        String[][] fields={{"folio","COTIZACIÓN"},{"cliente","CLIENTE"},{"contacto","CONTACTO"},{"fecha","FECHA"},{"ciudad","CIUDAD"},{"servicio","SERVICIO SOLICITADO"},{"sp","SP"},{"pago","T. PAGO"}};
        for(int i=0;i<fields.length;i++)t.fields.put(fields[i][0],new FieldDefinition(fields[i][0],fields[i][1],i,false));
        t.headerLines.add(new HeaderLine("TALLER ELECTRICO OLIVERIO SA DE CV",14,true,false,"#111111"));
        t.headerLines.add(new HeaderLine("Servicios de Pailería, Soldadura, Torno Industrial",10,false,false,"#333333"));
        t.headerLines.add(new HeaderLine("Tuberías en Acero Inoxidable, HDPE, Motores Eléctricos",10,false,false,"#333333"));
        t.headerLines.add(new HeaderLine("Renta de Maquinaria y Electromecánica en General",10,false,false,"#333333"));
        t.headerLines.add(new HeaderLine("www.teosa.mx",10,false,false,"#333333"));
        t.section1Title="DESCRIPCIÓN DEL SERVICIO";t.section2Title="ALCANCES";t.section3Title="NOTAS";
        t.photoCommentStyle.setItalic(false);t.photoCommentStyle.setColor("#111111");return t;
    }
    private String quotationFooterAddress;
    private String quotationFooterContact;
    public String getQuotationFooterAddress(){return quotationFooterAddress==null?"Jimenez S/N entre Sociedad Mutualista y Arnulfo R. Gómez, Col. Tierra Blanca, Navojoa, Sonora, CP. 85820":quotationFooterAddress;}
    public void setQuotationFooterAddress(String value){quotationFooterAddress=value;}
    public String getQuotationFooterContact(){return quotationFooterContact==null?"R.F.C. TEO-010430-4K9  Tel: (642)422-3240   Correo: teosa1@hotmail.com":quotationFooterContact;}
    public void setQuotationFooterContact(String value){quotationFooterContact=value;}
    private String name = "Formato predeterminado";
    private long lastUsedAt;
    private Map<String, FieldDefinition> fields = new LinkedHashMap<>();
    private List<HeaderLine> headerLines = new ArrayList<>();
    private TextStyle photoCommentStyle = new TextStyle();
    private TextStyle photoSubtitleStyle = categoryDefaults();
    private String photoSubtitleBackgroundColor;
    public String getPhotoSubtitleBackgroundColor() {
        return photoSubtitleBackgroundColor == null ? "#ffffff" : photoSubtitleBackgroundColor;
    }
    public void setPhotoSubtitleBackgroundColor(String value) { photoSubtitleBackgroundColor = value; }
    public String subtitleBackground(String legacyColor) {
        return photoSubtitleBackgroundColor == null ? legacyColor : photoSubtitleBackgroundColor;
    }
    public TextStyle getPhotoSubtitleStyle() {
        if (photoSubtitleStyle == null) photoSubtitleStyle = categoryDefaults();
        return photoSubtitleStyle;
    }
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
    private String totalBackgroundColor;
    public String getTotalBackgroundColor() { return totalBackgroundColor == null ? getSectionBackgroundColor() : totalBackgroundColor; }
    public void setTotalBackgroundColor(String color) { totalBackgroundColor = color; }
    private String section3Title = "3.  REPORTE FOTOGRÁFICO DEL ANTES, DURANTE Y DESPUÉS DE REALIZAR EL TRABAJO:";
    private boolean startPhotosOnNewPage;
    private String photoBorderMode = PHOTO_BORDER_FULL_PAGE;
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

    /** One field color; also updates legacy per-field values for saved documents. */
    public String fieldsBackgroundColor() {
        return orderedFields().stream().findFirst().map(FieldDefinition::getBackgroundColor).orElse("#d9d9d9");
    }
    public void setFieldsBackgroundColor(String color) {
        getFields().values().forEach(field -> field.setBackgroundColor(color));
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
    public boolean isStartPhotosOnNewPage() { return startPhotosOnNewPage; }
    public void setStartPhotosOnNewPage(boolean startPhotosOnNewPage) {
        this.startPhotosOnNewPage = startPhotosOnNewPage;
    }
    public String getPhotoBorderMode() {
        return PHOTO_BORDER_CONTENT.equals(photoBorderMode)
                ? PHOTO_BORDER_CONTENT : PHOTO_BORDER_FULL_PAGE;
    }
    public void setPhotoBorderMode(String photoBorderMode) {
        this.photoBorderMode = PHOTO_BORDER_CONTENT.equals(photoBorderMode)
                ? PHOTO_BORDER_CONTENT : PHOTO_BORDER_FULL_PAGE;
    }
    public boolean isPhotoBorderFullPage() {
        return PHOTO_BORDER_FULL_PAGE.equals(getPhotoBorderMode());
    }
    public Map<String, String> getPresetValues() { if (presetValues == null) presetValues = new LinkedHashMap<>(); return presetValues; }
    public void setPresetValues(Map<String, String> presetValues) { this.presetValues = presetValues; }
}
