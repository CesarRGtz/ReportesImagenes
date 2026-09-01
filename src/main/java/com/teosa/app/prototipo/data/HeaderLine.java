package com.teosa.app.prototipo.data;

public class HeaderLine {
    private String text = "";
    private TextStyle style = new TextStyle();

    public HeaderLine() {}
    public HeaderLine(String text, double size, boolean bold, boolean italic, String color) {
        this.text = text;
        style.setFontSize(size);
        style.setBold(bold);
        style.setItalic(italic);
        style.setColor(color);
    }

    public String getText() { return text == null ? "" : text; }
    public void setText(String text) { this.text = text; }
    public TextStyle getStyle() { if (style == null) style = new TextStyle(); return style; }
    public void setStyle(TextStyle style) { this.style = style; }
}
