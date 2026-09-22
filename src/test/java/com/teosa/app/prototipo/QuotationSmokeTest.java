package com.teosa.app.prototipo;
import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import com.teosa.app.prototipo.infrastructure.network.*;
import com.teosa.app.prototipo.infrastructure.pdf.*;
import com.teosa.app.prototipo.application.AppServices;
import java.math.BigDecimal;
import java.nio.file.*;
import java.net.ServerSocket;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;

public final class QuotationSmokeTest {
    static Quotation sample(){
        Quotation q=new Quotation();q.setValue("folio","CDM-954");q.setValue("cliente","COBRE DEL MAYO");
        q.setValue("contacto","ING. ERNESTO MORALES");q.setValue("ciudad","ALAMOS, SON");
        q.setValue("servicio","EMBOBINADO DE MOTOR DE AGUA DE CALDERA 3 HP");q.setValue("sp","10062282");q.setValue("pago","A CREDITO");
        QuotationLine l=new QuotationLine();l.setCode("P01");l.setDescription("MOTOR 3 HP, 480 VOLTS, 60 HZ");l.setUnitPrice(new BigDecimal("4740"));
        l.setScope("A01 Pruebas eléctricas HI-POT y SURGE (antes y después de la reparación).\nA02 Análisis de vibraciones\nA03 Embobinado de estator\nA04 Suministro y cambio de rodamientos\nA05 Limpieza y pintura\nA06 Reporte fotográfico del servicio realizado");q.getLines().add(l);return q;
    }
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("teosa-quotation-test-");System.setProperty("teosa.data.dir",directory.toString());
        Quotation q=sample();q.validate();
        check(q.subtotal().equals(new BigDecimal("4740.00")),"Subtotal del Excel");check(q.tax().equals(new BigDecimal("758.40")),"IVA del Excel");check(q.total().equals(new BigDecimal("5498.40")),"Total del Excel");
        check(AmountInWords.pesos(q.total()).equals("CINCO MIL CUATROCIENTOS NOVENTA Y OCHO PESOS 40/100 M.N."),"Cantidad con letra");
        check(AmountInWords.pesos(new BigDecimal("21001.01")).startsWith("VEINTIÚN MIL UN PESOS 01"),"Apócope");
        QuotationLine l=q.getLines().getFirst();l.setQuantity(new BigDecimal("1.5"));
        try{q.validate();throw new AssertionError("Permitió cantidad fraccionaria");}catch(IllegalArgumentException expected){}l.setQuantity(BigDecimal.ONE);
        l.setUnitPrice(new BigDecimal("0.335"));l.setQuantity(new BigDecimal("3"));check(q.subtotal().equals(new BigDecimal("1.01")),"Redondeo decimal");l.setQuantity(BigDecimal.ONE);l.setUnitPrice(new BigDecimal("4740"));
        TemplateDefinition template=TemplateDefinition.quotationDefaults();template.setName("Compartida");template.getPresetValues().put("ciudad","ALAMOS, SON");
        ReportSnapshot snapshot=new ReportSnapshot();snapshot.setReportId("cotizacion-prueba-1234");snapshot.setQuotation(q);snapshot.setTemplate(template);
        OfflineQueue queue=new OfflineQueue();LocalTemplateCache cache=new LocalTemplateCache();
        AppServices services=new AppServices(queue,new AssetCodecAdapter(),new ConnectionRuntimeAdapter(),cache);
        int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}
        AppConfig config=new AppConfig();config.setRole(AppConfig.Role.SECONDARY);config.setServerUrl("http://127.0.0.1:"+port);
        services.initialize(config);
        try{
            check(services.saveReport(snapshot).isQueued(),"Cotización debe guardarse sin servidor");services.saveTemplate(template);
            check(services.listReports("CDM-954",DocumentKind.QUOTATION).size()==1,"Búsqueda local");
            check(services.listReports("",DocumentKind.REPORT).isEmpty(),"Separación del historial local");
            var local=services.listVersions(snapshot.getReportId()).getFirst();
            check(services.loadReport(snapshot.getReportId(),local).getQuotation().total().equals(q.total()),"Reabrir pendiente");
            check(services.listTemplates(DocumentKind.QUOTATION).size()==1,"Plantilla sin conexión");
            ServerStorage storage=new ServerStorage(directory.resolve("server"));
            try(LocalReportServer server=new LocalReportServer(port,storage)){
                server.start();long deadline=System.currentTimeMillis()+18000;
                while(System.currentTimeMillis()<deadline && (storage.listReports("").isEmpty()||queue.count()!=0))Thread.sleep(100);
                check(queue.count()==0 && storage.listReports("").size()==1,"Subida automática al reconectar");
                services.listTemplates();check(storage.listTemplates().size()==1,"Sincronización de plantilla");
                ReportSnapshot report=new ReportSnapshot();report.setReportId("reporte-prueba-1234");report.setReport(new ReporteServicio("Otro","15/09/2026","Taller","R-1","","","",""));report.setTemplate(TemplateDefinition.defaults());
                check(!services.saveReport(report).isQueued(),"Reporte conectado");
                check(services.listReports("",DocumentKind.QUOTATION).size()==1 && services.listReports("",DocumentKind.REPORT).size()==1,"Historial remoto separado");
                TemplateDefinition photo=TemplateDefinition.defaults();photo.setName("Compartida");services.saveTemplate(photo);
                check(storage.listTemplates().size()==2,"Plantillas homónimas separadas");
                q.setNotes("Segunda versión");services.saveReport(snapshot);
                check(services.listVersions(snapshot.getReportId()).size()==2,"Historial de versiones");
                services.deleteTemplate(template.storageName());check(storage.listTemplates().size()==1 && storage.listTemplates().getFirst().getKind()==DocumentKind.REPORT,"Borrado aislado de plantilla");
            }
        }finally{services.close();}
        check(services.listReports("",DocumentKind.QUOTATION).size()==1,"Copia después de desconectar");
        var version=services.listVersions(snapshot.getReportId()).getFirst();check(services.loadReport(snapshot.getReportId(),version).getQuotation()!=null,"Reabrir sincronizado sin servidor");
        ReportSnapshot legacy=JsonSupport.GSON.fromJson("{\"reportId\":\"legacy-123\",\"report\":{\"cliente\":\"Legado\"}}",ReportSnapshot.class);
        check(legacy.getKind()==DocumentKind.REPORT && legacy.client().equals("Legado"),"Compatibilidad JSON anterior");
        Path outputs=Path.of("work/verification");Files.createDirectories(outputs);Path pdf=outputs.resolve("cotizacion-ejemplo.pdf");
        q=sample();QuotationPdfGenerator.generate(pdf,q,template);
        try(var doc=Loader.loadPDF(pdf.toFile())){
            String text=new PDFTextStripper().getText(doc);
            check(text.contains("CDM-954")&&text.contains("5,498.40")&&text.contains("A CREDITO"),"Contenido PDF del Excel");
            check(doc.getNumberOfPages()==1,"El ejemplo cabe en una página");
            check(text.contains("Jimenez S/N entre Sociedad Mutualista")&&text.contains("teosa1@hotmail.com"),"Default footer missing");
            ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,110),"png",outputs.resolve("cotizacion-ejemplo.png").toFile());
        }
        template.getFields().get("contacto").setVisible(false);template.getFields().put("custom",new FieldDefinition("custom","REFERENCIA ESPECIAL",8,true));q.setValue("custom","Dato personalizado");
        template.setQuotationFooterAddress("DIRECCIÓN PERSONALIZADA");template.setQuotationFooterContact("CONTACTO PERSONALIZADO");
        q.getLines().getFirst().setScope("Alcance extenso de prueba.\n".repeat(130));Path longPdf=outputs.resolve("cotizacion-multipagina.pdf");QuotationPdfGenerator.generate(longPdf,q,template);
        try(var doc=Loader.loadPDF(longPdf.toFile())){
            String text=new PDFTextStripper().getText(doc);check(doc.getNumberOfPages()>1,"Paginación de alcance largo");
            check(!text.contains("ING. ERNESTO")&&text.replaceAll("\\s+"," ").contains("REFERENCIA ESPECIAL")&&text.contains("Dato personalizado"),"Personalización aplicada al PDF");
            check(text.contains("5,498.40"),"Total presente tras paginar");
            for(int page=1;page<=doc.getNumberOfPages();page++){
                PDFTextStripper footer=new PDFTextStripper();footer.setStartPage(page);footer.setEndPage(page);String pageText=footer.getText(doc);
                check(pageText.contains("DIRECCIÓN PERSONALIZADA")&&pageText.contains("CONTACTO PERSONALIZADO"),"Edited footer missing on page "+page);
            }
        }
        System.out.println("QUOTATION_OFFLINE_SYNC_PDF_OK");
    }
}
