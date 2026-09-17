package com.teosa.app.prototipo.domain;

import com.teosa.app.prototipo.domain.ReporteServicio;

public class ReportSnapshot {
    private Quotation quotation;
    public Quotation getQuotation(){return quotation;}
    public void setQuotation(Quotation value){quotation=value;}
    public DocumentKind getKind(){return quotation==null?DocumentKind.REPORT:DocumentKind.QUOTATION;}
    public boolean hasDocument(){return report!=null || quotation!=null;}
    public String client(){return quotation==null?report.getCliente():quotation.value("cliente");}
    public String date(){return quotation==null?report.getFecha():quotation.value("fecha");}
    public String area(){return quotation==null?report.getArea():quotation.value("servicio");}
    public String reference(){return quotation==null?report.getRemision():quotation.value("folio")+" "+quotation.value("sp");}
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
