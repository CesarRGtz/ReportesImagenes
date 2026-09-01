package com.teosa.app.prototipo.data;

public class TextStyle {
    private String fontFamily = "Arial";
    private double fontSize = 11;
    private String color = "#334155";
    private boolean bold;
    private boolean italic = true;

    public String getFontFamily() { return fontFamily == null ? "Arial" : fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
    public double getFontSize() { return fontSize <= 0 ? 11 : fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = fontSize; }
    public String getColor() { return color == null ? "#334155" : color; }
    public void setColor(String color) { this.color = color; }
    public boolean isBold() { return bold; }
    public void setBold(boolean bold) { this.bold = bold; }
    public boolean isItalic() { return italic; }
    public void setItalic(boolean italic) { this.italic = italic; }
}
