package com.teosa.app.prototipo;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;

public final class DesktopSmokeTest {
    private static void snapshot(Stage stage,String name)throws Exception {
        stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
        WritableImage shot=stage.getScene().snapshot(null);BufferedImage image=new BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,shot.getPixelReader().getArgb(x,y));
        Files.createDirectories(Path.of("work/verification"));ImageIO.write(image,"png",Path.of("work/verification/ui-"+name+".png").toFile());
    }
    private static void click(Parent root,String id){
        if(!(root.lookup("#"+id) instanceof Button button))throw new AssertionError("No existe "+id);
        button.fire();root.applyCss();root.layout();
    }
    private static void leaveHome(BorderPane shell,boolean accept){
        Parent before=(Parent)shell.getCenter();AtomicBoolean prompted=new AtomicBoolean();
        Platform.runLater(()->{
            for(Window window:new ArrayList<>(Window.getWindows())){
                if(window.isShowing() && window.getScene().getRoot() instanceof DialogPane pane){
                    ButtonType choice=pane.getButtonTypes().stream().filter(t->accept
                        ? t.getButtonData()==ButtonBar.ButtonData.OK_DONE || t.getButtonData()==ButtonBar.ButtonData.YES
                        : t.getButtonData()==ButtonBar.ButtonData.CANCEL_CLOSE || t.getButtonData()==ButtonBar.ButtonData.NO).findFirst().orElseThrow();
                    prompted.set(true);((Button)pane.lookupButton(choice)).fire();return;
                }
            }
        });
        click(shell,"return-home");
        if(!prompted.get())throw new AssertionError("No confirmó los cambios sin guardar");
        if(!accept && shell.getCenter()!=before)throw new AssertionError("Salió pese a cancelar");
        if(accept && !"server-home".equals(shell.getCenter().getId()))throw new AssertionError("No volvió al menú al confirmar");
    }
    private static void fillsShell(BorderPane shell,Parent content){
        var bounds=content.getBoundsInParent();
        if(Math.abs(bounds.getMinX())>1 || Math.abs(bounds.getMinY())>1 || Math.abs(bounds.getWidth()-shell.getWidth())>1 || Math.abs(bounds.getHeight()-shell.getHeight())>1)
            throw new AssertionError("Reportes deja márgenes vacíos: "+bounds+" / "+shell.getWidth()+"x"+shell.getHeight());
    }
    private static void fx(Runnable operation)throws Exception {
        CompletableFuture<Void> done=new CompletableFuture<>();Platform.runLater(()->{try{operation.run();done.complete(null);}catch(Throwable e){done.completeExceptionally(e);}});done.get(25,TimeUnit.SECONDS);
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("teosa.data.dir",Files.createTempDirectory("teosa-desktop-test-").toString());
        Platform.startup(()->{});Platform.setImplicitExit(false);
        try {
            for(String mode:new String[]{"REPORTS","QUOTATIONS","SERVER"}) {
                System.setProperty("teosa.launch.mode",mode);App app=new App();Stage[] stage={null};
                fx(()->{try{stage[0]=new Stage();app.start(stage[0]);stage[0].setX(-10000);stage[0].setY(-10000);}catch(Exception ex){throw new RuntimeException(ex);}});
                Thread.sleep(1800);
                fx(()->{
                    if(stage[0].getScene()==null)throw new AssertionError("Sin escena "+mode);
                    if(mode.equals("SERVER")) {
                        BorderPane shell=(BorderPane)stage[0].getScene().getRoot();
                        Parent home=(Parent)shell.getCenter();
                        if(!"server-home".equals(home.getId()) || shell.getTop()!=null || !shell.lookupAll(".menu-bar").isEmpty())throw new AssertionError("El servidor no inició en el menú principal");
                        try{snapshot(stage[0],"SERVER-HOME");}catch(Exception ex){throw new RuntimeException(ex);}
                        click(shell,"open-reports");Parent photo=(Parent)shell.getCenter();
                        fillsShell(shell,photo);
                        click(shell,"return-home");if(shell.getCenter()!=home)throw new AssertionError("No salió del reporte vacío");
                        click(shell,"open-reports");
                        TextField client=(TextField)photo.lookup("#txtCliente");client.setText("Cliente conservado");
                        leaveHome(shell,false);leaveHome(shell,true);
                        click(shell,"open-quotations");Parent quote=(Parent)shell.getCenter();
                        TextField folio=(TextField)quote.lookup("#quotation-folio");folio.setText("COT-PERSISTE");
                        leaveHome(shell,false);leaveHome(shell,true);click(shell,"open-reports");
                        if(shell.getCenter()!=photo || !client.getText().equals("Cliente conservado"))throw new AssertionError("Perdió los datos del reporte");
                        if(photo.lookupAll("#return-home").size()!=1)throw new AssertionError("Botón de inicio duplicado");
                        try{snapshot(stage[0],"SERVER-REPORTS");}catch(Exception ex){throw new RuntimeException(ex);}
                        leaveHome(shell,true);click(shell,"open-quotations");
                        if(shell.getCenter()!=quote || !folio.getText().equals("COT-PERSISTE"))throw new AssertionError("Perdió los datos de la cotización");
                    } else if(stage[0].getScene().getRoot().lookup("#return-home")!=null || stage[0].getScene().getRoot().lookup("#server-home")!=null){
                        throw new AssertionError("Una secundaria muestra la navegación del servidor");
                    }
                });
                Thread.sleep(1400);
                fx(()->{try{
                    snapshot(stage[0],mode);
                    if(mode.equals("QUOTATIONS")){
                        Parent root=stage[0].getScene().getRoot();
                        if(root.lookup(".app-topbar")==null || root.lookup(".form-card")==null || root.lookup(".floating-action-bar")==null)throw new AssertionError("Falta el diseño compartido");
                        root.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals("Personalizar formato")).map(n->(Button)n).findFirst().orElseThrow().fire();
                        root.applyCss();root.layout();
                        if(root.lookup("#quotation-customization")==null)throw new AssertionError("No abrió el panel de personalización");
                        snapshot(stage[0],"QUOTATION-DRAWER");
                        Parent drawer=(Parent)root.lookup("#quotation-customization");
                        TextField name=drawer.lookupAll(".text-field").stream().filter(n->n instanceof TextField f&&f.getText().equals("Cotización predeterminada")).map(n->(TextField)n).findFirst().orElseThrow();
                        name.setText("Formato de prueba");
                        drawer.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals("Cerrar personalización")).map(n->(Button)n).findFirst().orElseThrow().fire();
                        if(((BorderPane)root).getRight()!=null)throw new AssertionError("No cerró la personalización");
                    }
                    stage[0].hide();app.stop();
                }catch(Exception ex){throw new RuntimeException(ex);}});
                System.out.println("DESKTOP_"+mode+"_OK");
            }
        }finally{Platform.exit();}
    }
}
