package com.teosa.app.prototipo.presentation;
import javafx.print.*;
import javafx.scene.image.*;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.stage.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Locale;

public final class PdfPrintSupport {
    private PdfPrintSupport(){}
    public static void print(Path pdf,Window owner,String title)throws Exception {
        PrinterJob job=PrinterJob.createPrinterJob();
        if(job==null)throw new IllegalStateException("No hay una impresora disponible.");
        job.getJobSettings().setJobName(title);
        if(!job.showPrintDialog(owner)){job.cancelJob();return;}
        try {
            if(job.getPrinter().getName().toLowerCase(Locale.ROOT).contains("pdf")){
                FileChooser chooser=new FileChooser();chooser.setTitle("Guardar impresión en PDF");chooser.setInitialFileName("Cotizacion.pdf");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));
                var target=chooser.showSaveDialog(owner);if(target!=null)Files.copy(pdf,target.toPath(),StandardCopyOption.REPLACE_EXISTING);job.cancelJob();return;
            }
            PageLayout layout=job.getPrinter().createPageLayout(Paper.NA_LETTER,PageOrientation.PORTRAIT,Printer.MarginType.HARDWARE_MINIMUM);
            job.getJobSettings().setPageLayout(layout);
            try(var document=Loader.loadPDF(pdf.toFile())){
                PDFRenderer renderer=new PDFRenderer(document);
                var ranges=job.getJobSettings().getPageRanges();
                for(int i=0;i<document.getNumberOfPages();i++){
                    int page=i+1;if(ranges!=null&&ranges.length>0&&java.util.Arrays.stream(ranges).noneMatch(r->page>=r.getStartPage()&&page<=r.getEndPage()))continue;
                    BufferedImage bitmap=renderer.renderImageWithDPI(i,200,ImageType.RGB);
                    WritableImage image=new WritableImage(bitmap.getWidth(),bitmap.getHeight());
                    int[] pixels=bitmap.getRGB(0,0,bitmap.getWidth(),bitmap.getHeight(),null,0,bitmap.getWidth());
                    image.getPixelWriter().setPixels(0,0,bitmap.getWidth(),bitmap.getHeight(),PixelFormat.getIntArgbInstance(),pixels,0,bitmap.getWidth());
                    ImageView view=new ImageView(image);view.setFitWidth(layout.getPaper().getWidth());view.setFitHeight(layout.getPaper().getHeight());view.setPreserveRatio(true);view.setManaged(false);
                    view.relocate(-layout.getLeftMargin(),-layout.getTopMargin());Pane pagePane=new Pane(view);
                    pagePane.resize(layout.getPrintableWidth(),layout.getPrintableHeight());pagePane.setClip(new Rectangle(layout.getPrintableWidth(),layout.getPrintableHeight()));
                    if(!job.printPage(layout,pagePane))throw new IllegalStateException("La impresión fue cancelada o falló.");
                }
            }
            if(!job.endJob())throw new IllegalStateException("La impresora no confirmó la recepción del documento.");
        }catch(Exception ex){job.cancelJob();throw ex;}
    }
}
