package com.teosa.app.prototipo;
import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.pdf.PdfReportGenerator;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;

public class PhotoPageLayoutSmokeTest {
    static final Color[] COLORS={Color.RED,Color.GREEN,Color.BLUE,Color.MAGENTA,Color.CYAN};
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static int[] bounds(BufferedImage image,Color color){
        int[] b={9999,9999,-1,-1};
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            if((image.getRGB(x,y)&0xffffff)==(color.getRGB()&0xffffff)){
                b[0]=Math.min(b[0],x);b[1]=Math.min(b[1],y);b[2]=Math.max(b[2],x);b[3]=Math.max(b[3],y);
            }
        }return b;
    }
    public static void main(String[] args)throws Exception{
        Path dir=Path.of("work/verification/photo-page-layout");Files.createDirectories(dir);
        ReporteServicio report=new ReporteServicio("Cliente","21/09/2026","Área","R1","C1","F1","Equipo","Servicio");
        CategoriaFotografica cat=new CategoriaFotografica("FOTOGRAFÍAS DE PRUEBA");
        for(int i=0;i<5;i++){
            BufferedImage img=new BufferedImage(i==4?600:750,1000,BufferedImage.TYPE_INT_RGB);
            var g=img.createGraphics();g.setColor(COLORS[i]);g.fillRect(0,0,img.getWidth(),img.getHeight());g.dispose();
            Path file=dir.resolve("photo"+i+".png");ImageIO.write(img,"png",file.toFile());
            FotoEvidencia photo=new FotoEvidencia(file.toAbsolutePath().toString(),"");photo.setAncho(ReportLayout.MAX_PHOTO_WIDTH*.28);cat.agregarFotografia(photo);
        }
        report.agregarCategoriaFotografica(cat);
        TemplateDefinition template=TemplateDefinition.defaults();template.setStartPhotosOnNewPage(true);
        Path pdf=dir.resolve("five-photos.pdf");PdfReportGenerator.generar(pdf.toFile(),report,template);
        try(var doc=Loader.loadPDF(pdf.toFile())){
            check(doc.getNumberOfPages()==2,"Expected two pages, got "+doc.getNumberOfPages());
            BufferedImage page=new PDFRenderer(doc).renderImageWithDPI(1,72);ImageIO.write(page,"png",dir.resolve("five-photos.png").toFile());
            int[][] b=new int[5][];for(int i=0;i<5;i++){b[i]=bounds(page,COLORS[i]);check(b[i][2]>0,"Missing photo "+i);}
            check(Math.abs((b[0][3]-b[0][1])-(b[3][3]-b[3][1]))<=1,"Second row shrinks");
            check(Math.abs((b[1][0]-b[0][2])-(b[4][0]-b[3][2]))<=1,"Unequal image gaps");
            check(b[0][0]==b[3][0],"Rows do not start at same x");
            int signatureTop=-1;
            for(int y=b[3][3]+1;y<740;y++){
                int dark=0;for(int x=80;x<530;x++)if(((page.getRGB(x,y)>>16)&255)<180)dark++;
                if(dark>400){signatureTop=y;break;}
            }
            check(signatureTop>0&&Math.abs((signatureTop-b[3][3])-(b[3][1]-b[0][3]))<=2,"Signature gap must equal image row gap: "+signatureTop);
        }
        // A full-size portrait must move to a new page, never shrink to the remaining space.
        cat.getFotografias().subList(1,5).clear();
        PdfReportGenerator.generar(dir.resolve("optional-signature-gap.pdf").toFile(),report,template);
        try(var doc=Loader.loadPDF(dir.resolve("optional-signature-gap.pdf").toFile())) {
            check(doc.getNumberOfPages()==2,"Optional gap added an unnecessary page");
            BufferedImage page=new PDFRenderer(doc).renderImageWithDPI(1,72);
            ImageIO.write(page,"png",dir.resolve("optional-signature-gap.png").toFile());
            int[] b=bounds(page,Color.RED);int top=-1;
            for(int y=b[3]+1;y<740;y++) {
                int dark=0;for(int x=80;x<530;x++)if(((page.getRGB(x,y)>>16)&255)<180)dark++;
                if(dark>400){top=y;break;}
            }
            check(Math.abs(top-b[3]-8-CuadroFirmas.MARGEN_SUPERIOR_OPCIONAL)<3,"Expected photo-row gap, got "+(top-b[3]));
        }
        check(CuadroFirmas.margenSuperior(CuadroFirmas.alto(report.getFirmas())+CuadroFirmas.MARGEN+10,report.getFirmas())==0,"Tight page must have no top margin");
        cat.getFotografias().get(0).setAncho(ReportLayout.MAX_PHOTO_WIDTH);
        template.setStartPhotosOnNewPage(false);PdfReportGenerator.generar(dir.resolve("large-photo.pdf").toFile(),report,template);
        try(var doc=Loader.loadPDF(dir.resolve("large-photo.pdf").toFile())){
            check(doc.getNumberOfPages()>=3,"Large photo or signatures were compressed");
            int[] b=bounds(new PDFRenderer(doc).renderImageWithDPI(1,72),Color.RED);
            double expected=ReportLayout.pagePhotoSize(750,1000,ReportLayout.MAX_PHOTO_WIDTH)[1];
            check(Math.abs(b[3]-b[1]+1-expected)<2,"Photo does not preserve page-based height");
        }
        verifyPreview(dir);
        System.out.println("PAGE_BASED_PHOTOS_GAPS_AND_FLOWING_SIGNATURES_OK");
    }
    static void verifyPreview(Path dir)throws Exception {
        javafx.application.Platform.startup(()->{});
        java.util.concurrent.CompletableFuture<Void> done=new java.util.concurrent.CompletableFuture<>();
        javafx.application.Platform.runLater(()->{
            try {
                var controller=new com.teosa.app.prototipo.presentation.PrimaryController(null,null,null,dir);
                FotoEvidencia photo=new FotoEvidencia(dir.resolve("photo0.png").toAbsolutePath().toString(), "Comentario");
                photo.setAncho(ReportLayout.MAX_PHOTO_WIDTH*.28);
                var prepare=controller.getClass().getDeclaredMethod("prepararFotoPreview",FotoEvidencia.class,int.class,double.class);prepare.setAccessible(true);
                double[] size=ReportLayout.pagePhotoSize(750,1000,photo.getAncho());
                Object data=prepare.invoke(controller,photo,0,size[0]);
                var create=controller.getClass().getDeclaredMethod("crearCeldaFotoPreview",data.getClass(),int.class,int.class,double.class,double.class);create.setAccessible(true);
                var first=(javafx.scene.layout.VBox)create.invoke(controller,data,0,0,650.0,size[0]);
                var second=(javafx.scene.layout.VBox)create.invoke(controller,data,0,0,80.0,size[0]);
                var a=(javafx.scene.image.ImageView)first.getChildren().get(0);
                var b=(javafx.scene.image.ImageView)second.getChildren().get(0);
                check(Math.abs(a.getFitHeight()-b.getFitHeight())<.01,"Preview shrinks to remaining space");
                check(Math.abs(a.getFitWidth()-first.getPrefWidth())<.01,"Preview cell leaves an invisible horizontal gap");
                check(Math.abs(a.getFitHeight()-size[1])<.01,"Preview and PDF size mismatch");
                done.complete(null);
            } catch(Throwable ex){done.completeExceptionally(ex);}
        });
        try{done.get(20,java.util.concurrent.TimeUnit.SECONDS);}finally{javafx.application.Platform.exit();}
    }

}
