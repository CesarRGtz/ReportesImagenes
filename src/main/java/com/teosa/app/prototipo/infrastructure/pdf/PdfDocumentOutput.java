package com.teosa.app.prototipo.infrastructure.pdf;
import com.teosa.app.prototipo.application.port.DocumentOutput;
import com.teosa.app.prototipo.domain.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
public final class PdfDocumentOutput implements DocumentOutput {
    public void report(File file, ReporteServicio report, TemplateDefinition template)throws Exception {PdfReportGenerator.generar(file,report,template);}
    public void quotation(File file, Quotation quotation, TemplateDefinition template)throws Exception {QuotationPdfGenerator.generate(file.toPath(),quotation,template);}
    public List<byte[]> preview(Quotation quotation,TemplateDefinition template)throws Exception {
        Path pdf=Files.createTempFile("teosa-cotizacion-preview-",".pdf");
        try {
            quotation(pdf.toFile(),quotation,template);
            try(var document=Loader.loadPDF(pdf.toFile())) {
                List<byte[]> result=new ArrayList<>(); PDFRenderer renderer=new PDFRenderer(document);
                for(int i=0;i<document.getNumberOfPages();i++) {
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    ImageIO.write(renderer.renderImageWithDPI(i,96),"png",bytes); result.add(bytes.toByteArray());
                }
                return result;
            }
        } finally {Files.deleteIfExists(pdf);}
    }
}
