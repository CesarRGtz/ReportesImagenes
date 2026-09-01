package com.teosa.app.prototipo.data;

public class FieldDefinition {
    private String key;
    private String label;
    private String backgroundColor = "#d9d9d9";
    private boolean visible = true;
    private int order;
    private boolean custom;

    public FieldDefinition() {}

    public FieldDefinition(String key, String label, int order, boolean custom) {
        this.key = key;
        this.label = label;
        this.order = order;
        this.custom = custom;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getLabel() { return label == null ? "" : label; }
    public void setLabel(String label) { this.label = label; }
    public String getBackgroundColor() { return backgroundColor == null ? "#d9d9d9" : backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public boolean isCustom() { return custom; }
    public void setCustom(boolean custom) { this.custom = custom; }
}
