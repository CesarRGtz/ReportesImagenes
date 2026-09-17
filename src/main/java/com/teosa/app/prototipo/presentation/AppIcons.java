package com.teosa.app.prototipo.presentation;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Affine;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/** Locally bundled Google Material Symbols; decorative graphics keep visible button labels. */
public final class AppIcons {
 private static final Properties DATA=new Properties();
 static {
  try(var input=AppIcons.class.getResourceAsStream("/com/teosa/app/prototipo/icons/icons.properties")){
   if(input==null)throw new IOException("Missing bundled icons");
   DATA.load(new InputStreamReader(input,StandardCharsets.UTF_8));
  }catch(IOException ex){throw new ExceptionInInitializerError(ex);}
 }
 private AppIcons(){}
 public static Pane graphic(String name,double size,ObservableValue<? extends Paint> color){
  String path=DATA.getProperty(name+".path");if(path==null)throw new IllegalArgumentException("Unknown icon: "+name);
  double[] box=Arrays.stream(DATA.getProperty(name+".viewBox").split(" ")).mapToDouble(Double::parseDouble).toArray();
  SVGPath svg=new SVGPath();svg.setContent(path);
  if(DATA.containsKey(name+".strokeWidth")){
   svg.setFill(javafx.scene.paint.Color.TRANSPARENT);svg.strokeProperty().bind(color);
   svg.setStrokeWidth(Double.parseDouble(DATA.getProperty(name+".strokeWidth")));
   svg.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);svg.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
  }else svg.fillProperty().bind(color);
  double scale=size/box[2];svg.getTransforms().add(new Affine(scale,0,-box[0]*scale,0,scale,-box[1]*scale));
  Pane pane=new Pane(svg);pane.setMinSize(size,size);pane.setPrefSize(size,size);pane.setMaxSize(size,size);pane.setMouseTransparent(true);pane.setFocusTraversable(false);pane.getStyleClass().add("app-icon");return pane;
 }
 public static Button button(){return new Button();}
 public static Button button(String text){Button button=new Button(text);decorate(button);return button;}
 public static void decorate(ButtonBase button){
  if(button.getGraphic()!=null)return;
  String name=forText(button.getText());if(name==null)return;
  button.setGraphic(graphic(name,18,button.textFillProperty()));button.setGraphicTextGap(7);
 }
 public static void decorateTree(Node node){
  if(node instanceof ButtonBase button)decorate(button);
  if(node instanceof ScrollPane scroll){if(scroll.getContent()!=null)decorateTree(scroll.getContent());}
  else if(node instanceof TitledPane pane){if(pane.getContent()!=null)decorateTree(pane.getContent());}
  else if(node instanceof SplitPane split){for(Node child:split.getItems())decorateTree(child);}
  else if(node instanceof Parent parent){for(Node child:parent.getChildrenUnmodifiable())decorateTree(child);}
 }
 private static String forText(String value){
  if(value==null)return null;
  String text=Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim();
  if(text.equals("inicio"))return "home";
  if(text.contains("menu principal"))return "arrow_back";
  if(text.equals("reportes"))return "summarize";
  if(text.equals("cotizacion")||text.equals("cotizaciones"))return "request_quote";
  if(text.equals("configuracion"))return "settings";
  if(text.contains("sin guardar"))return null;
  if(text.startsWith("guardar"))return "save";
  if(text.contains("pdf"))return "picture_as_pdf";
  if(text.contains("imprimir"))return "print";
  if(text.contains("historial"))return "history";
  if(text.startsWith("eliminar")||text.startsWith("quitar"))return "delete";
  if(text.startsWith("personalizar"))return "tune";
  if(text.startsWith("restaurar")||text.startsWith("usar logo"))return "restart_alt";
  if(text.contains("firma"))return "draw";
  if(text.startsWith("agregar")&&(text.contains("foto")||text.contains("imagen")))return "add_photo_alternate";
  if(text.contains("imagen")||text.contains("foto"))return "photo_library";
  if(text.startsWith("agregar")||text.startsWith("nuev"))return "add";
  if(text.startsWith("editar"))return "edit";
  if(text.startsWith("buscar"))return "search";
  if(text.equals("actualizar"))return "refresh";
  if(text.startsWith("abrir")||text.startsWith("importar"))return "folder_open";
  if(text.startsWith("aplicar")||text.equals("aceptar"))return "check";
  if(text.equals("cancelar")||text.equals("cerrar"))return "close";
  if(text.equals("subir"))return "arrow_upward";
  if(text.equals("bajar"))return "arrow_downward";
  // Compact arrow-only controls retain their original arrows instead of duplicating them.
  return null;
 }
}
