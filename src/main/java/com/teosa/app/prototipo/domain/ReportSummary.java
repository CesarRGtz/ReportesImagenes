package com.teosa.app.prototipo.domain;

public class ReportSummary {
    private DocumentKind kind;
    public DocumentKind getKind(){return kind==null?DocumentKind.REPORT:kind;}
    public void setKind(DocumentKind v){kind=v;}
    private String reportId;
    private String client;
    private String date;
    private String area;
    private String remision;
    private long modifiedAt;
    private long registeredAt;
    public long getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(long value) { registeredAt = value; }
    public boolean registeredIn(java.time.YearMonth month, java.time.ZoneId zone) {
        return registeredAt > 0 && java.time.YearMonth.from(
                java.time.Instant.ofEpochMilli(registeredAt).atZone(zone)).equals(month);
    }
    private int versionCount;
    private int pendingCount;
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
    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = Math.max(0, pendingCount); }
    public boolean isPending() { return pendingCount > 0; }
    public String getLastAuthor() { return lastAuthor; }
    public void setLastAuthor(String lastAuthor) { this.lastAuthor = lastAuthor; }
}
