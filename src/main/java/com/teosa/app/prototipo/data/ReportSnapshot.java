package com.teosa.app.prototipo.data;

import com.teosa.app.prototipo.ReporteServicio;

public class ReportSnapshot {
    private String reportId;
    private int version;
    private long savedAt;
    private String author;
    private String computer;
    private ReporteServicio report;
    private TemplateDefinition template;

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public long getSavedAt() { return savedAt; }
    public void setSavedAt(long savedAt) { this.savedAt = savedAt; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getComputer() { return computer; }
    public void setComputer(String computer) { this.computer = computer; }
    public ReporteServicio getReport() { return report; }
    public void setReport(ReporteServicio report) { this.report = report; }
    public TemplateDefinition getTemplate() { return template; }
    public void setTemplate(TemplateDefinition template) { this.template = template; }
}
