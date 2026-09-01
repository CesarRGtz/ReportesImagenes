package com.teosa.app.prototipo.data;

public class AssetPayload {
    private String assetId;
    private String fileName;
    private String dataBase64;
    private boolean original;

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getDataBase64() { return dataBase64; }
    public void setDataBase64(String dataBase64) { this.dataBase64 = dataBase64; }
    public boolean isOriginal() { return original; }
    public void setOriginal(boolean original) { this.original = original; }
}
