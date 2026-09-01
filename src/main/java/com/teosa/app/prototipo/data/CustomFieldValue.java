package com.teosa.app.prototipo.data;

public class CustomFieldValue {
    private String key;
    private String value;

    public CustomFieldValue() {}
    public CustomFieldValue(String key, String value) { this.key = key; this.value = value; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value == null ? "" : value; }
    public void setValue(String value) { this.value = value; }
}
