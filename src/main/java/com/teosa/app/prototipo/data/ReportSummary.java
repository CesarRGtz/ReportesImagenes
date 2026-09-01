package com.teosa.app.prototipo.data;

public class ReportSummary {
    private String reportId;
    private String client;
    private String date;
    private String area;
    private String remision;
    private long modifiedAt;
    private int versionCount;
    private String lastAuthor;

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getRemision() { return remision; }
    public void setRemision(String remision) { this.remision = remision; }
    public long getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(long modifiedAt) { this.modifiedAt = modifiedAt; }
    public int getVersionCount() { return versionCount; }
    public void setVersionCount(int versionCount) { this.versionCount = versionCount; }
    public String getLastAuthor() { return lastAuthor; }
    public void setLastAuthor(String lastAuthor) { this.lastAuthor = lastAuthor; }
}
