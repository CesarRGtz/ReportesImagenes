package com.teosa.app.prototipo.presentation;
import com.teosa.app.prototipo.App;
import com.teosa.app.prototipo.domain.CatalogEntry;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import java.util.*;
public final class NewCatalogDialog {
 private NewCatalogDialog(){}
 public static Optional<List<CatalogEntry>> ask(Window owner,List<CatalogEntry> entries){
  Dialog<List<CatalogEntry>> dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle("Datos nuevos detectados");dialog.setHeaderText("¿Deseas guardar estos datos para volver a usarlos?");
  VBox content=new VBox(10);List<CheckBox> checks=new ArrayList<>();
  Label help=new Label("Se compartirán con los demás equipos. Sin conexión quedarán pendientes de sincronizar.");help.setWrapText(true);content.getChildren().add(help);
  for(CatalogEntry entry:entries){CheckBox check=new CheckBox((entry.getKind()==CatalogEntry.Kind.CLIENT?"Cliente: ":"Alcance: ")+entry.getText());check.setWrapText(true);check.setSelected(true);checks.add(check);content.getChildren().add(check);}
  ScrollPane scroll=new ScrollPane(content);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(Math.min(380,75+entries.size()*50));dialog.getDialogPane().setContent(scroll);dialog.getDialogPane().setPrefWidth(570);
  ButtonType save=new ButtonType("Guardar seleccionados",ButtonBar.ButtonData.YES),skip=new ButtonType("Continuar sin guardar",ButtonBar.ButtonData.NO);
  dialog.getDialogPane().getButtonTypes().addAll(save,skip,ButtonType.CANCEL);App.applyTheme(dialog.getDialogPane());
  dialog.setResultConverter(button->{if(button==skip)return List.of();if(button!=save)return null;List<CatalogEntry> selected=new ArrayList<>();for(int i=0;i<entries.size();i++)if(checks.get(i).isSelected())selected.add(entries.get(i));return selected;});
  return dialog.showAndWait();
 }
}
