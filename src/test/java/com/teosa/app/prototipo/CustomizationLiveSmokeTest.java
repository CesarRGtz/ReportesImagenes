package com.teosa.app.prototipo;

import com.teosa.app.prototipo.application.AppServices;
import com.teosa.app.prototipo.application.port.DocumentOutput;
import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import com.teosa.app.prototipo.infrastructure.network.*;
import com.teosa.app.prototipo.infrastructure.pdf.*;
import com.teosa.app.prototipo.presentation.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.*;

public final class CustomizationLiveSmokeTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void fx(Runnable action)throws Exception{
        CompletableFuture<Void> done=new CompletableFuture<>();Platform.runLater(()->{try{action.run();done.complete(null);}catch(Throwable ex){done.completeExceptionally(ex);}});done.get(20,TimeUnit.SECONDS);
    }
    static void click(Parent root,String text){root.applyCss();root.layout();root.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(text)).map(n->(Button)n).findFirst().orElseThrow().fire();root.applyCss();root.layout();}
    static TitledPane section(Parent root,String title){return root.lookupAll(".titled-pane").stream().map(n->(TitledPane)n).filter(p->p.getText().equals(title)).findFirst().orElseThrow();}
    static void expand(Parent root,String name){TitledPane p=section(root,name);p.setAnimated(false);p.setExpanded(true);root.applyCss();root.layout();}
    static double offset(ScrollPane p){return p.getVvalue()*Math.max(0,p.getContent().getLayoutBounds().getHeight()-p.getViewportBounds().getHeight());}
    static final class RecordingOutput implements DocumentOutput {
        final PdfDocumentOutput delegate=new PdfDocumentOutput();
        volatile TemplateDefinition last;final AtomicInteger renders=new AtomicInteger();
        public void report(File f,ReporteServicio r,TemplateDefinition t)throws Exception{delegate.report(f,r,t);}
        public void quotation(File f,Quotation q,TemplateDefinition t)throws Exception{delegate.quotation(f,q,t);}
        public List<byte[]> preview(Quotation q,TemplateDefinition t)throws Exception{List<byte[]> result=delegate.preview(q,t);last=t;renders.incrementAndGet();return result;}
    }
    static void waitRender(RecordingOutput output,int previous)throws Exception{
        long until=System.currentTimeMillis()+20000;while(output.renders.get()<=previous&&System.currentTimeMillis()<until)Thread.sleep(100);
        check(output.renders.get()>previous,"Preview did not render after editing");Thread.sleep(300);fx(()->{});
    }
    static void screenshot(Stage stage,String filename)throws Exception{
        WritableImage shot=stage.getScene().snapshot(null);BufferedImage image=new BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,shot.getPixelReader().getArgb(x,y));
        Path file=Path.of("work/verification",filename);Files.createDirectories(file.getParent());ImageIO.write(image,"png",file.toFile());
    }
    public static void main(String[] args)throws Exception {
        Path temp=Files.createTempDirectory("teosa-live-customization-");System.setProperty("teosa.data.dir",temp.toString());
        AppServices services=new AppServices(new OfflineQueue(),new AssetCodecAdapter(),new ConnectionRuntimeAdapter(),new LocalTemplateCache());
        AppConfig config=new AppConfig();config.setRole(AppConfig.Role.SECONDARY);services.initialize(config);
        Platform.startup(()->{});Platform.setImplicitExit(false);
        RecordingOutput output=new RecordingOutput();JsonDocumentCodec codec=new JsonDocumentCodec();
        Stage[] stage={null};QuotationController[] quote={null};PrimaryController[] report={null};
        Parent[] drawer={null};TextField[] editor={null},input={null};ScrollPane[] preview={null},custom={null};double[] positions=new double[2];
        try{
            fx(()->{
                quote[0]=new QuotationController(services,codec,output);stage[0]=new Stage();
                Scene scene=new Scene(quote[0].view(),1560,850);scene.getStylesheets().add(App.class.getResource("app.css").toExternalForm());stage[0].setScene(scene);stage[0].setX(-10000);stage[0].show();
            });waitRender(output,0);
            fx(()->{
                Parent root=stage[0].getScene().getRoot();click(root,"Personalizar formato");drawer[0]=(Parent)root.lookup("#quotation-customization");
                expand(drawer[0],"Encabezado");expand(drawer[0],"Campos del formulario");
                TitledPane fields=section(drawer[0],"Campos del formulario");
                check(fields.lookupAll(".color-picker").size()==1,"Fields must have one shared color");
                check(fields.lookupAll(".check-box").isEmpty(),"Visibility control still present");
                check(fields.lookupAll(".button").stream().noneMatch(n->n instanceof Button b&&List.of("Subir","Bajar","↑","↓").contains(b.getText())),"Field reorder still present");
                editor[0]=(TextField)drawer[0].lookup("#format-field-cliente");input[0]=(TextField)root.lookup("#quotation-cliente");input[0].setText("Cliente conservado");editor[0].requestFocus();
                preview[0]=(ScrollPane)root.lookup(".preview-scroll");custom[0]=(ScrollPane)drawer[0].lookup(".drawer-scroll");
                root.applyCss();root.layout();preview[0].setVvalue(.42);custom[0].setVvalue(.70);
            });Thread.sleep(700);
            int count=output.renders.get();
            fx(()->{
                positions[0]=offset(preview[0]);positions[1]=offset(custom[0]);
                editor[0].setText("EMPRESA SOLICITANTE");((ColorPicker)drawer[0].lookup("#format-fields-color")).setValue(Color.web("#aaccee"));
                check(stage[0].getScene().getRoot().lookup("#quotation-cliente")==input[0],"Field input replaced during typing");
                check(editor[0].getScene().getFocusOwner()==editor[0],"Editor lost focus");
            });waitRender(output,count);
            fx(()->{
                Parent root=stage[0].getScene().getRoot();
                check(root.lookup("#quotation-customization")==drawer[0],"Drawer closed or replaced");
                check(section(drawer[0],"Campos del formulario").isExpanded()&&section(drawer[0],"Encabezado").isExpanded(),"Section collapsed");
                check(Math.abs(offset(preview[0])-positions[0])<2,"Preview scrolled: "+positions[0]+" -> "+offset(preview[0]));
                check(Math.abs(offset(custom[0])-positions[1])<2,"Customization scrolled");
                check(output.last.getFields().get("cliente").getLabel().equals("EMPRESA SOLICITANTE"),"Label did not reach renderer");
                check(output.last.getFields().values().stream().allMatch(f->f.getBackgroundColor().equals("#aaccee")),"Shared color did not reach all fields");
                check(output.last.orderedFields().stream().map(FieldDefinition::getKey).toList().equals(TemplateDefinition.quotationDefaults().orderedFields().stream().map(FieldDefinition::getKey).toList()),"Field order changed");
                check(drawer[0].lookupAll(".button").stream().noneMatch(n->n instanceof Button b&&b.getText().equals("Agregar campo personalizado")),"Add field button remains");
                expand(drawer[0],"Contenido");
                TextField title=(TextField)drawer[0].lookup("#content-table").lookup(".text-field");title.setText("DETALLE DEL SERVICIO");
                ((ColorPicker)drawer[0].lookup("#format-table-color")).setValue(Color.web("#bbccdd"));
                ((ColorPicker)drawer[0].lookup("#format-total-color")).setValue(Color.web("#ccddaa"));
                ((ColorPicker)drawer[0].lookup("#format-table-color")).setValue(Color.web("#ddeeff"));
                CheckBox bold=(CheckBox)drawer[0].lookup("#content-text").lookup(".check-box");bold.setSelected(true);
                try{screenshot(stage[0],"personalizacion-cotizacion.png");}catch(Exception ex){throw new RuntimeException(ex);}
            });count=output.renders.get();waitRender(output,count);
            check(output.last.getSection1Title().equals("DETALLE DEL SERVICIO")&&output.last.getPhotoCommentStyle().isBold(),"Section and font changes are not live");
            check(output.last.getFields().values().stream().allMatch(f->f.getBackgroundColor().equals("#aaccee")),"New field lost shared color");
            check(output.last.getSectionBackgroundColor().equals("#ddeeff")&&output.last.getTotalBackgroundColor().equals("#ccddaa"),"Table and total colors must update independently");
            final DialogPane[] modal={null};
            count=output.renders.get();
            fx(()->{
                ((TextField)drawer[0].lookup("#header-text-0")).setText("ENCABEZADO EN VIVO");
                ((Button)drawer[0].lookup("#header-style-0")).fire();
                modal[0]=javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane p&&"header-style-dialog".equals(p.getId())).map(w->(DialogPane)w.getScene().getRoot()).findFirst().orElseThrow();
                modal[0].applyCss();modal[0].layout();
                check(modal[0].lookupAll(".color-picker").size()==1,"Modal must edit only the selected header field");
                ((ColorPicker)modal[0].lookup(".color-picker")).setValue(Color.web("#335577"));
            });waitRender(output,count);
            fx(()->{
                check(modal[0].getScene().getWindow().isShowing(),"Modal closed before preview update");
                try{screenshot((Stage)modal[0].getScene().getWindow(),"encabezado-modal.png");}catch(Exception ex){throw new RuntimeException(ex);}
                check(output.last.getHeaderLines().getFirst().getText().equals("ENCABEZADO EN VIVO"),"Modal text did not update preview while open");
                check(output.last.getHeaderLines().getFirst().getStyle().getColor().equals("#335577"),"Modal style did not update preview");
                check(!output.last.getHeaderLines().get(1).getStyle().getColor().equals("#335577"),"Modal changed another field");
                check(section(drawer[0],"Contenido").isExpanded(),"Content collapsed while editing header");
                ((Button)modal[0].lookupButton(modal[0].getButtonTypes().getFirst())).fire();
            });
            TemplateDefinition roundtrip=codec.copy(output.last,TemplateDefinition.class);check(roundtrip.getFields().get("cliente").getBackgroundColor().equals("#aaccee"),"Shared color not persisted");
            check(roundtrip.getTotalBackgroundColor().equals("#ccddaa"),"Total color not persisted");
            fx(()->{
                quote[0].dispose();
                try{
                    FXMLLoader loader=new FXMLLoader(App.class.getResource("primary.fxml"));
                    loader.setControllerFactory(type->{report[0]=new PrimaryController(services,codec,output,temp.resolve("images"));return report[0];});
                    Parent root=loader.load();stage[0].getScene().setRoot(root);click(root,"Personalizar formato");
                    drawer[0]=(Parent)root.lookup("#customizationDrawer");expand(drawer[0],"Encabezado");expand(drawer[0],"Campos del formulario");
                    preview[0]=(ScrollPane)root.lookup(".preview-scroll");custom[0]=(ScrollPane)root.lookup("#customizationScrollPane");
                    editor[0]=(TextField)drawer[0].lookup("#format-field-area");editor[0].requestFocus();root.applyCss();root.layout();
                    preview[0].setVvalue(.4);custom[0].setVvalue(.65);
                }catch(Exception ex){throw new RuntimeException(ex);}
            });Thread.sleep(500);
            fx(()->{
                positions[0]=offset(preview[0]);positions[1]=offset(custom[0]);
                editor[0].setText("ÁREA DEL SERVICIO");((ColorPicker)drawer[0].lookup("#format-fields-color")).setValue(Color.web("#aaccee"));
            });Thread.sleep(400);
            fx(()->{
                check(Math.abs(offset(preview[0])-positions[0])<2,"Report preview scrolled");check(Math.abs(offset(custom[0])-positions[1])<2,"Report customization scrolled");
                check(editor[0].getScene().getFocusOwner()==editor[0],"Report editor lost focus");
                check(stage[0].getScene().getRoot().lookupAll(".label").stream().anyMatch(n->n instanceof Label l&&l.getText().equals("ÁREA DEL SERVICIO")),"Report label did not update");
                check(drawer[0].lookupAll(".button").stream().noneMatch(n->n instanceof Button b&&b.getText().equals("Agregar campo personalizado")),"Report add field button remains");
                check(section(drawer[0],"Campos del formulario").isExpanded()&&section(drawer[0],"Encabezado").isExpanded(),"Report sections closed on add");
            });
            verifyPdfCoordinates();System.out.println("LIVE_CUSTOMIZATION_SCROLL_FIELDS_AND_SCOPE_ALIGNMENT_OK");
        }finally{fx(()->{if(quote[0]!=null)quote[0].dispose();if(report[0]!=null)report[0].dispose();if(stage[0]!=null)stage[0].hide();});services.close();Platform.exit();}
    }
    static void verifyPdfCoordinates()throws Exception{
        Quotation quote=new Quotation();QuotationLine line=new QuotationLine();line.setCode("P01");line.setDescription("Servicio");line.setScopeBreakdown(true);
        for(int i=0;i<2;i++){QuotationScope scope=new QuotationScope(i==0?"Limpieza":"Revisión del motor con descripción larga para comprobar que los renglones conservan su alineación");scope.setCost(new java.math.BigDecimal(i==0?"120.50":"79.50"));line.getScopes().add(scope);}quote.getLines().add(line);
        Path pdf=Files.createTempFile("scope-alignment-",".pdf");QuotationPdfGenerator.generate(pdf,quote,TemplateDefinition.quotationDefaults());
        try(var document=Loader.loadPDF(pdf.toFile())){
            Map<String,float[]> found=new HashMap<>();PDFTextStripper stripper=new PDFTextStripper(){
                @Override protected void writeString(String text,List<TextPosition> positions)throws java.io.IOException{
                    String all=positions.stream().map(TextPosition::getUnicode).reduce("",String::concat);
                    for(String needle:List.of("A01","A02","Limpieza","Revisión","120.50","79.50")){
                        int at=all.indexOf(needle);if(at>=0&&at<positions.size())found.put(needle,new float[]{positions.get(at).getXDirAdj(),positions.get(at).getYDirAdj()});
                    }super.writeString(text,positions);
                }
            };stripper.getText(document);
            for(String code:List.of("A01","A02")){check(found.containsKey(code),"Missing "+code);check(found.get(code)[0]<84,"Scope code still in description column");}
            for(String[] pair:new String[][]{{"A01","Limpieza","120.50"},{"A02","Revisión","79.50"}}){
                check(found.get(pair[1])[0]>84,"Scope text outside description column");
                check(Math.abs(found.get(pair[0])[1]-found.get(pair[1])[1])<1,"Code misaligned with scope");
                check(Math.abs(found.get(pair[0])[1]-found.get(pair[2])[1])<1,"Cost misaligned with scope");
                check(found.get(pair[2])[0]>300&&found.get(pair[2])[0]<366,"Cost outside right edge of description");
            }
        }finally{Files.deleteIfExists(pdf);}
    }
}
