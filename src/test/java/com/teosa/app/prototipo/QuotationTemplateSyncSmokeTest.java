package com.teosa.app.prototipo;
import com.teosa.app.prototipo.application.*;
import com.teosa.app.prototipo.application.port.*;
import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import com.teosa.app.prototipo.infrastructure.network.*;
import com.teosa.app.prototipo.presentation.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class QuotationTemplateSyncSmokeTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void fx(Runnable work)throws Exception{CompletableFuture<Void> done=new CompletableFuture<>();Platform.runLater(()->{try{work.run();done.complete(null);}catch(Throwable ex){done.completeExceptionally(ex);}});done.get(15,TimeUnit.SECONDS);}
    static void waitUntil(BooleanSupplier condition)throws Exception{long end=System.currentTimeMillis()+20000;while(!condition.getAsBoolean()&&System.currentTimeMillis()<end)Thread.sleep(100);check(condition.getAsBoolean(),"Timed out awaiting update");fx(()->{});}
    static void click(Parent root,String text){root.applyCss();root.layout();root.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(text)).map(n->(Button)n).findFirst().orElseThrow().fire();}
    static final class Output implements DocumentOutput {
        volatile TemplateDefinition last;final JsonDocumentCodec codec=new JsonDocumentCodec();
        public List<byte[]> preview(Quotation q,TemplateDefinition t){last=codec.copy(t,TemplateDefinition.class);return List.of();}
        public void report(File f,ReporteServicio r,TemplateDefinition t){}public void quotation(File f,Quotation q,TemplateDefinition t){}
    }
    public static void main(String[] args)throws Exception{
        Path temp=Files.createTempDirectory("teosa-template-sync-");System.setProperty("teosa.data.dir",temp.resolve("secondary").toString());
        ServerStorage storage=new ServerStorage(temp.resolve("server"));
        TemplateDefinition defaults=TemplateDefinition.quotationDefaults();defaults.setSection1Title("SERVIDOR INICIAL");storage.saveTemplate(defaults);
        try(LocalReportServer server=new LocalReportServer(0,storage)){
            server.start();AppServices services=new AppServices(new OfflineQueue(),new AssetCodecAdapter(),new ConnectionRuntimeAdapter(),new LocalTemplateCache());
            AppConfig config=new AppConfig();config.setRole(AppConfig.Role.SECONDARY);config.setServerUrl("http://127.0.0.1:"+server.getPort());services.initialize(config);
            Platform.startup(()->{});Platform.setImplicitExit(false);Stage[] stage={null};QuotationController[] controller={null};Parent[] root={null};Output output=new Output();
            try{
                fx(()->{controller[0]=new QuotationController(services,new JsonDocumentCodec(),output);root[0]=controller[0].view();stage[0]=new Stage();stage[0].setScene(new Scene(root[0],1500,900));stage[0].setX(-10000);stage[0].show();});
                waitUntil(()->output.last!=null&&output.last.getSection1Title().equals("SERVIDOR INICIAL"));
                fx(()->{TextField folio=(TextField)root[0].lookup("#quotation-folio");check(!folio.isEditable(),"Auto folio editable");check(folio.getText().matches("TEO\\d{2}-001"),"Automatic folio missing");((TextField)root[0].lookup("#quotation-cliente")).setText("Cliente conservado");});
                defaults.setSection1Title("SERVIDOR ACTUALIZADO");storage.saveTemplate(defaults);
                waitUntil(()->output.last.getSection1Title().equals("SERVIDOR ACTUALIZADO"));
                fx(()->{
                    check(((TextField)root[0].lookup("#quotation-cliente")).getText().equals("Cliente conservado"),"Auto template update lost values");
                    click(root[0],"Personalizar formato");Parent drawer=(Parent)root[0].lookup("#quotation-customization");drawer.applyCss();drawer.layout();
                    TextField name=drawer.lookupAll(".text-field").stream().filter(n->n instanceof TextField t&&t.getText().equals("Cotización predeterminada")).map(n->(TextField)n).findFirst().orElseThrow();name.setText("Nueva plantilla");
                    ComboBox<?> combo=(ComboBox<?>)root[0].lookup("#quotation-templates");check(combo.getItems().stream().anyMatch(t->((TemplateDefinition)t).getName().equals("Cotización predeterminada")),"Renaming draft mutated default selector item");
                    click(drawer,"Guardar plantilla");
                });
                waitUntil(()->{try{return storage.listTemplates().stream().anyMatch(t->t.getName().equals("Nueva plantilla"));}catch(Exception ex){return false;}});Thread.sleep(1200);
                fx(()->{
                    ComboBox<?> combo=(ComboBox<?>)root[0].lookup("#quotation-templates");check(combo.getItems().size()==2,"Selector lost default or duplicated a template");check(((TemplateDefinition)combo.getValue()).getName().equals("Nueva plantilla"),"Saved template not selected");
                    Platform.runLater(()->{for(Window w:List.copyOf(Window.getWindows()))if(w.isShowing()&&w.getScene().getRoot() instanceof DialogPane p){((Button)p.lookupButton(ButtonType.YES)).fire();break;}});
                    click((Parent)root[0].lookup("#quotation-customization"),"Eliminar plantilla guardada");
                });
                waitUntil(()->output.last.getName().equals("Cotización predeterminada"));
                fx(()->{check(root[0].lookup("#quotation-customization")==null,"Deleting active template did not close drawer");check(((TextField)root[0].lookup("#quotation-cliente")).getText().equals("Cliente conservado"),"Delete reset document data");});
                System.out.println("SECONDARY_TEMPLATE_STARTUP_REFRESH_SAVE_DELETE_OK");
            }finally{fx(()->{if(controller[0]!=null)controller[0].dispose();if(stage[0]!=null)stage[0].hide();});services.close();Platform.exit();}
        }
    }
}
