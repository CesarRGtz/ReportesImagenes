package com.teosa.app.prototipo;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.*;
import java.nio.file.*;
import java.util.concurrent.*;

public final class CatalogEditorSmokeTest {
 static void fx(Runnable work)throws Exception {CompletableFuture<Void> done=new CompletableFuture<>();Platform.runLater(()->{try{work.run();done.complete(null);}catch(Throwable ex){done.completeExceptionally(ex);}});done.get(20,TimeUnit.SECONDS);}
 static void click(Parent root,String text){root.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(text)).map(n->(Button)n).findFirst().orElseThrow().fire();root.applyCss();root.layout();}
 static TextField field(Parent root,String suffix){return root.lookupAll(".text-field").stream().filter(n->n.getId()!=null&&n.getId().endsWith(suffix)).map(n->(TextField)n).findFirst().orElseThrow();}
 public static void main(String[] args)throws Exception {
  System.setProperty("teosa.data.dir",Files.createTempDirectory("teosa-catalog-ui-").toString());System.setProperty("teosa.launch.mode","QUOTATIONS");
  Platform.startup(()->{});Platform.setImplicitExit(false);App app=new App();Stage[] stage={null};
  try{
   fx(()->{try{stage[0]=new Stage();app.start(stage[0]);}catch(Exception ex){throw new RuntimeException(ex);}});Thread.sleep(2200);
   fx(()->{TextField client=(TextField)stage[0].getScene().lookup("#quotation-cliente");client.requestFocus();client.setText("CO");});
   fx(()->{
    ContextMenu menu=Window.getWindows().stream().filter(w->w instanceof ContextMenu && w.isShowing()).map(w->(ContextMenu)w).findFirst().orElseThrow();
    if(menu.getItems().size()!=4)throw new AssertionError("Expected four suggestions");menu.getItems().getFirst().fire();menu.hide();
    Parent root=stage[0].getScene().getRoot();if(((TextField)root.lookup("#quotation-cliente")).getText().equals("CO"))throw new AssertionError("Did not complete selected client");
    click(root,"Agregar partida");
    for(String text:new String[]{"Alcance de prueba uno","Alcance de prueba dos"}){
      TextArea draft=root.lookupAll(".text-area").stream().filter(n->n.getId()!=null&&n.getId().endsWith("-scope-draft")).map(n->(TextArea)n).findFirst().orElseThrow();draft.setText(text);root.applyCss();root.layout();
    }
    if(root.lookupAll(".button").stream().anyMatch(n->n instanceof Button b && (b.getText().equals("Agregar alcance")||b.getText().equals("Guardar alcance")||b.getText().equals("Guardar cliente"))))throw new AssertionError("Obsolete catalog buttons remain");
    CheckBox breakdown=root.lookupAll(".check-box").stream().filter(n->n.getId()!=null&&n.getId().endsWith("-breakdown")).map(n->(CheckBox)n).findFirst().orElseThrow();breakdown.fire();root.applyCss();root.layout();
    field(root,"-scope-0-cost").setText("120.50");field(root,"-scope-1-cost").setText("79.50");
    TextField price=field(root,"-unit-price");if(price.isEditable()||new java.math.BigDecimal(price.getText()).compareTo(new java.math.BigDecimal("200"))!=0)throw new AssertionError("Derived price must be read-only and equal scope sum");
    click(root,"Quitar");price=field(root,"-unit-price");if(new java.math.BigDecimal(price.getText()).compareTo(new java.math.BigDecimal("79.50"))!=0)throw new AssertionError("Removal did not update price");
    if(root.lookupAll(".label").stream().filter(n->n instanceof Label l&&l.getText().equals("A01")).count()!=1)throw new AssertionError("Scope numbering did not reset");
   });
   System.out.println("CATALOG_EDITOR_SUGGESTIONS_AND_COSTS_OK");
  }finally{fx(()->{if(stage[0]!=null)stage[0].hide();app.stop();});Platform.exit();}
 }
}
