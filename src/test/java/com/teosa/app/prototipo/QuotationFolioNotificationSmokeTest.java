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
import javafx.scene.layout.VBox;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.*;

public final class QuotationFolioNotificationSmokeTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void fx(Runnable work)throws Exception{QuotationTemplateSyncSmokeTest.fx(work);}
    static DialogPane dialog(){return Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane).map(w->(DialogPane)w.getScene().getRoot()).findFirst().orElse(null);}
    static void waitFx(java.util.function.BooleanSupplier condition)throws Exception{
        long end=System.currentTimeMillis()+25000;AtomicBoolean ok=new AtomicBoolean();
        do{fx(()->ok.set(condition.getAsBoolean()));if(ok.get())return;Thread.sleep(100);}while(System.currentTimeMillis()<end);throw new AssertionError("Timed out waiting for UI");
    }
    public static void main(String[] args)throws Exception{
        Path temp=Files.createTempDirectory("teosa-folio-notice-");System.setProperty("teosa.data.dir",temp.resolve("secondary").toString());
        ServerStorage storage=new ServerStorage(temp.resolve("server"));AtomicBoolean online=new AtomicBoolean(true);
        try(LocalReportServer server=new LocalReportServer(0,storage)){
            server.start();HttpReportClient wire=new HttpReportClient("http://127.0.0.1:"+server.getPort());
            RemoteDocuments remote=(RemoteDocuments)Proxy.newProxyInstance(RemoteDocuments.class.getClassLoader(),new Class<?>[]{RemoteDocuments.class},(proxy,method,arguments)->{
                if(!online.get()){if(method.getName().equals("health"))return false;throw new java.io.IOException("Test disconnected");}
                try{return method.invoke(wire,arguments);}catch(InvocationTargetException ex){throw ex.getCause();}
            });
            ConnectionRuntime runtime=new ConnectionRuntime(){
                public RemoteDocuments initialize(AppConfig c){return remote;}public void reconnect(RemoteDocuments r,AppConfig c){}public String user(){return "Test";}public String computer(){return "Test";}public void close(){}
            };
            OfflineQueue queue=new OfflineQueue();AppServices services=new AppServices(queue,new AssetCodecAdapter(),runtime,new LocalTemplateCache(),new CatalogStore(()->temp.resolve("catalog")));AppConfig config=new AppConfig();config.setRole(AppConfig.Role.SECONDARY);services.initialize(config);
            String knownClient=services.listCatalog().stream().filter(e->e.getKind()==CatalogEntry.Kind.CLIENT).findFirst().orElseThrow().getText();
            Platform.startup(()->{});Platform.setImplicitExit(false);Stage[] stage={null};QuotationController[] controller={null};Parent[] root={null};
            try{
                fx(()->{controller[0]=new QuotationController(services,new JsonDocumentCodec(),new QuotationTemplateSyncSmokeTest.Output());root[0]=controller[0].view();stage[0]=new Stage();stage[0].setScene(new Scene(root[0],1500,900));stage[0].setX(-10000);stage[0].show();});
                waitFx(()->((TextField)root[0].lookup("#quotation-folio")).getText().matches("TEO\\d{2}-001"));
                online.set(false);waitFx(()->((TextField)root[0].lookup("#quotation-folio")).getText().contains("PEND"));
                fx(()->{
                    ((TextField)root[0].lookup("#quotation-cliente")).setText(knownClient);
                    QuotationTemplateSyncSmokeTest.click(root[0],"Agregar partida");
                    TextField price=root[0].lookupAll(".text-field").stream().filter(n->n.getId()!=null&&n.getId().endsWith("-unit-price")).map(n->(TextField)n).findFirst().orElseThrow();
                    VBox card=(VBox)price.getParent();card.getChildren().stream().filter(n->n instanceof TextArea).map(n->(TextArea)n).findFirst().orElseThrow().setText("Trabajo sin conexión");
                    QuotationTemplateSyncSmokeTest.click(root[0],"Guardar cotización");
                });
                waitFx(()->dialog()!=null);fx(()->{DialogPane pane=dialog();check(pane.getHeaderText().equals("Guardada en este equipo"),"Expected offline save confirmation");((Button)pane.lookupButton(ButtonType.OK)).fire();});
                check(queue.count()==1,"Missing queued quotation");
                wire.quotationFolio("other-computer",true);online.set(true);
                waitFx(()->dialog()!=null&&dialog().getHeaderText()!=null&&dialog().getHeaderText().startsWith("La cotización ahora usa"));
                fx(()->{
                    String expected=String.format("TEO%02d-002",java.time.LocalDate.now().getYear()%100);
                    check(((TextField)root[0].lookup("#quotation-folio")).getText().equals(expected),"Server correction not applied to open form");
                    check(((TextField)root[0].lookup("#quotation-cliente")).getText().equals(knownClient),"Correction discarded form data");
                    check(dialog().getContentText().contains("Anterior:")&&dialog().getContentText().contains(expected),"Notice lacks old and corrected number");
                    ((Button)dialog().lookupButton(ButtonType.OK)).fire();
                });
                check(queue.count()==0,"Queue not acknowledged");
                System.out.println("OFFLINE_SERVER_FOLIO_CORRECTION_NOTICE_AND_FORM_OK");
            }finally{fx(()->{for(Window window:List.copyOf(Window.getWindows()))if(window.isShowing())window.hide();if(controller[0]!=null)controller[0].dispose();});services.close();Platform.exit();}
        }
    }
}

