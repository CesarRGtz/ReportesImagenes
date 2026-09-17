package com.teosa.app.prototipo;
import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.*;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class SettingsAndCaptureSmokeTest {
 static Stage stage;static Parent root;
 static void fx(Runnable work)throws Exception{CompletableFuture<Void> done=new CompletableFuture<>();Platform.runLater(()->{try{work.run();done.complete(null);}catch(Throwable ex){done.completeExceptionally(ex);}});done.get(15,TimeUnit.SECONDS);}
 static void click(String text){root.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(text)).map(n->(Button)n).findFirst().orElseThrow().fire();root.applyCss();root.layout();}
 static void id(String id){((Button)root.lookup("#"+id)).fire();root.applyCss();root.layout();}
 static DialogPane dialog(){return Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene().getRoot() instanceof DialogPane).map(w->(DialogPane)w.getScene().getRoot()).findFirst().orElse(null);}
 static void respond(ButtonBar.ButtonData data)throws Exception {
  for(int i=0;i<100;i++){AtomicBoolean found=new AtomicBoolean();fx(()->{DialogPane pane=dialog();if(pane!=null){ButtonType type=pane.getButtonTypes().stream().filter(t->t.getButtonData()==data).findFirst().orElseThrow();((Button)pane.lookupButton(type)).fire();found.set(true);}});if(found.get())return;Thread.sleep(100);}throw new AssertionError("Dialog did not appear");
 }
 static void idle()throws Exception{Thread.sleep(1000);fx(()->{root.applyCss();root.layout();});}
 static void snapshot(String name)throws Exception{var shot=stage.getScene().snapshot(null);BufferedImage image=new BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,shot.getPixelReader().getArgb(x,y));Files.createDirectories(Path.of("work/verification"));ImageIO.write(image,"png",Path.of("work/verification/"+name+".png").toFile());}
 public static void main(String[] args)throws Exception {
  System.setProperty("teosa.data.dir",Files.createTempDirectory("teosa-settings-").toString());System.setProperty("teosa.launch.mode","SERVER");
  AppConfig config=new AppConfig();try(ServerSocket port=new ServerSocket(0)){config.setServerPort(port.getLocalPort());}ConfigStore.save(config);
  Platform.startup(()->{});Platform.setImplicitExit(false);App app=new App();
  try{
   fx(()->{try{stage=new Stage();app.start(stage);root=stage.getScene().getRoot();}catch(Exception ex){throw new RuntimeException(ex);}});idle();
   fx(()->id("server-settings"));fx(()->id("settings-quotations"));idle();
   fx(()->{TabPane tabs=(TabPane)root.lookup(".tab-pane");if(!tabs.getTabs().stream().map(Tab::getText).toList().equals(List.of("Clientes","Alcances","Plantillas")))throw new AssertionError("Quotation sections");try{snapshot("configuracion-cotizacion");}catch(Exception ex){throw new RuntimeException(ex);}});
   fx(()->id("settings-reports"));idle();
   fx(()->{TabPane tabs=(TabPane)root.lookup(".tab-pane");if(tabs.getTabs().size()!=1||!tabs.getTabs().getFirst().getText().equals("Plantillas"))throw new AssertionError("Report templates");click("Nueva plantilla");});
   fx(()->{TextField name=root.lookupAll(".text-field").stream().filter(n->n instanceof TextField t&&t.getText().equals("Nueva plantilla")).map(n->(TextField)n).findFirst().orElseThrow();name.setText("Plantilla de prueba CRUD");click("Guardar plantilla");});idle();
   if(AppContext.services().listTemplates(DocumentKind.REPORT).stream().noneMatch(t->t.getName().equals("Plantilla de prueba CRUD")))throw new AssertionError("Template was not saved");
   fx(()->{ListView<TemplateDefinition> list=(ListView<TemplateDefinition>)root.lookup("#manage-templates");list.getSelectionModel().select(list.getItems().stream().filter(t->t.getName().equals("Plantilla de prueba CRUD")).findFirst().orElseThrow());Platform.runLater(()->{DialogPane pane=dialog();((Button)pane.lookupButton(ButtonType.YES)).fire();});click("Eliminar");});idle();
   if(AppContext.services().listTemplates(DocumentKind.REPORT).stream().anyMatch(t->t.getName().equals("Plantilla de prueba CRUD")))throw new AssertionError("Template was not deleted");
   fx(()->{click("Inicio");id("open-quotations");});idle();
   fx(()->{
    ((TextField)root.lookup("#quotation-cliente")).setText("Cliente nuevo prueba flujo");((TextField)root.lookup("#quotation-folio")).setText("FLUJO-001");click("Agregar partida");
    TextField price=root.lookupAll(".text-field").stream().filter(n->n.getId()!=null&&n.getId().endsWith("-unit-price")).map(n->(TextField)n).findFirst().orElseThrow();
    VBox card=(VBox)price.getParent();card.getChildren().stream().filter(n->n instanceof TextArea).map(n->(TextArea)n).findFirst().orElseThrow().setText("Mantenimiento de prueba");
    TextArea draft=root.lookupAll(".text-area").stream().filter(n->n.getId()!=null&&n.getId().endsWith("-scope-draft")).map(n->(TextArea)n).findFirst().orElseThrow();draft.setText("Alcance nuevo prueba flujo");
   });
   for(String action:List.of("Guardar cotización","Generar PDF","Imprimir")){fx(()->click(action));respond(ButtonBar.ButtonData.CANCEL_CLOSE);}
   if(!AppContext.services().listReports("").isEmpty())throw new AssertionError("Cancel performed document operation");
   if(AppContext.services().listCatalog().stream().anyMatch(e->e.getText().contains("nuevo prueba flujo")))throw new AssertionError("Cancel saved catalog data");
   fx(()->click("Guardar cotización"));respond(ButtonBar.ButtonData.YES);respond(ButtonBar.ButtonData.OK_DONE);
   if(AppContext.services().listReports("").size()!=1)throw new AssertionError("Accept did not save document");
   if(AppContext.services().listCatalog().stream().filter(e->e.getText().contains("nuevo prueba flujo")).count()!=2)throw new AssertionError("Accept did not save both new entries");
   fx(()->click("Guardar cotización"));respond(ButtonBar.ButtonData.OK_DONE);
   System.out.println("SETTINGS_TEMPLATES_AND_CAPTURE_ACTIONS_OK");
  }finally{fx(()->{stage.hide();app.stop();});Platform.exit();}
 }
}
