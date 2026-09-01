package com.teosa.app.prototipo.data;

public class SaveResponse {
    private boolean success;
    private boolean queued;
    private String reportId;
    private int version;
    private String message;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public boolean isQueued() { return queued; }
    public void setQueued(boolean queued) { this.queued = queued; }
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
