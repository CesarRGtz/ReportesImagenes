package com.teosa.app.prototipo;

import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import com.teosa.app.prototipo.infrastructure.pdf.PdfDocumentOutput;
import com.teosa.app.prototipo.presentation.*;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.*;
import java.io.IOException;

/** Composition root and desktop lifecycle. */
public class App extends Application {
 private static Scene scene;
 private PrimaryController reports;
 private QuotationController quotations;
 private Parent reportView;
 private BorderPane serverShell;
 private ServerHomeView serverHome;
 private final JsonDocumentCodec codec=new JsonDocumentCodec();
 private final PdfDocumentOutput output=new PdfDocumentOutput();
 @Override public void start(Stage stage)throws IOException {
  LaunchMode mode=LaunchMode.valueOf(System.getProperty("teosa.launch.mode","REPORTS"));
  if(mode==LaunchMode.QUOTATIONS && System.getProperty("teosa.data.dir")==null)
   System.setProperty("teosa.data.dir",AppDirectories.base().resolve("Cotizaciones").toString());
  AppConfig config=ConfigStore.load();config.setRole(mode.role());
  try{AppContext.services().initialize(config);}
  catch(IOException ex){Alert alert=new Alert(Alert.AlertType.ERROR,"No fue posible iniciar los servicios: "+ex.getMessage(),ButtonType.OK);alert.showAndWait();AppContext.services().close();stage.close();return;}
  Parent root;
  if(mode==LaunchMode.SERVER){
   serverShell=new BorderPane();serverShell.getStyleClass().add("app-shell");
   serverHome=new ServerHomeView(AppContext.services(),codec,()->{
    try{serverShell.setCenter(reports());}
    catch(IOException ex){Alert alert=new Alert(Alert.AlertType.ERROR,"No se pudo abrir Reportes: "+ex.getMessage(),ButtonType.OK);applyTheme(alert.getDialogPane());alert.showAndWait();}
   },()->serverShell.setCenter(quotations()));
   showHome();root=serverShell;
  }else root=mode==LaunchMode.QUOTATIONS?quotations():reports();
  Rectangle2D area=Screen.getPrimary().getVisualBounds();
  scene=new Scene(root,Math.max(640,Math.min(1400,area.getWidth()-40)),Math.max(500,Math.min(850,area.getHeight()-40)));
  var css=App.class.getResource("app.css");if(css!=null)scene.getStylesheets().add(css.toExternalForm());
  stage.setTitle(mode.title());stage.setScene(scene);
  stage.setOnCloseRequest(e->{if((reports!=null&&!reports.confirmarCierreSiHayCambios())||(quotations!=null&&!quotations.confirmClose()))e.consume();});
  stage.centerOnScreen();stage.show();
 }
 private Parent reports()throws IOException {
  if(reportView==null){
   FXMLLoader loader=new FXMLLoader(App.class.getResource("primary.fxml"));
   loader.setControllerFactory(type->{if(type==PrimaryController.class)return new PrimaryController(AppContext.services(),codec,output,AppDirectories.cache());throw new IllegalArgumentException(type.getName());});
   reportView=loader.load();reports=loader.getController();AppIcons.decorateTree(reportView);
   if(serverShell!=null)DesktopChrome.addServerHome(reportView,this::requestHome);
  }else reports.refreshSharedTemplates();return reportView;
 }
 private void requestHome(){
  if(serverShell.getCenter()==reportView && reports!=null && !reports.confirmarVolverAlMenu())return;
  if(quotations!=null && serverShell.getCenter()==quotations.view() && !quotations.confirmReturnHome())return;
  showHome();
 }
 private void showHome(){serverShell.setCenter(serverHome);serverHome.refresh();}
 private Parent quotations(){
  if(quotations==null){
   quotations=new QuotationController(AppContext.services(),codec,output);
   if(serverShell!=null)DesktopChrome.addServerHome(quotations.view(),this::requestHome);
  }
  quotations.refreshSharedTemplates();return quotations.view();
 }
 public static void setRoot(String fxml)throws IOException{scene.setRoot(new FXMLLoader(App.class.getResource(fxml+".fxml")).load());}
 public static void applyTheme(DialogPane pane){var css=App.class.getResource("app.css");if(css!=null&&!pane.getStylesheets().contains(css.toExternalForm()))pane.getStylesheets().add(css.toExternalForm());for(ButtonType type:pane.getButtonTypes())if(pane.lookupButton(type) instanceof ButtonBase button)AppIcons.decorate(button);}
 public static void main(String[] args){launch(args);}
 @Override public void stop(){if(reports!=null)reports.dispose();if(quotations!=null)quotations.dispose();AppContext.services().close();}
}
