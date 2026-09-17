package com.teosa.app.prototipo.presentation;
import com.teosa.app.prototipo.domain.CatalogEntry;
import javafx.scene.control.*;
import javafx.geometry.Side;
import java.util.List;
import java.util.function.Supplier;
public final class CatalogAutocomplete {
 private CatalogAutocomplete(){}
 public static void attach(TextInputControl field,CatalogEntry.Kind kind,Supplier<List<CatalogEntry>> source){
  ContextMenu menu=new ContextMenu();menu.setId("catalog-suggestions");boolean[] selecting={false};
  Runnable update=()->{
   if(selecting[0]||!field.isFocused()||field.getScene()==null)return;
   menu.getItems().clear();
   for(CatalogEntry entry:CatalogEntry.matches(source.get(),kind,field.getText())){
    Label label=new Label(entry.getText()+(entry.getCode().isBlank()?"":" · "+entry.getCode()));label.setWrapText(true);label.setMaxWidth(340);
    CustomMenuItem item=new CustomMenuItem(label,true);item.setOnAction(e->{selecting[0]=true;field.setText(entry.getText());field.positionCaret(field.getLength());menu.hide();selecting[0]=false;});menu.getItems().add(item);
   }
   if(menu.getItems().isEmpty())menu.hide();else if(!menu.isShowing())menu.show(field,Side.BOTTOM,0,0);
  };
  field.textProperty().addListener((o,a,b)->update.run());
  field.focusedProperty().addListener((o,a,b)->{if(b)update.run();});
  field.sceneProperty().addListener((o,a,b)->{if(b==null)menu.hide();});
 }
}
