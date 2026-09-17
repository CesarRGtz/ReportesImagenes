package com.teosa.app.prototipo.presentation;

import com.teosa.app.prototipo.App;
import com.teosa.app.prototipo.application.AppServices;
import com.teosa.app.prototipo.application.port.DocumentCodec;
import com.teosa.app.prototipo.domain.*;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.Consumer;

/** Shared administration shell; operations on catalogs never alter saved documents. */
public final class ServerSettingsView extends VBox {
 private final AppServices services;
 private final DocumentCodec codec;
 private final VBox body=new VBox(14);
 private final Label status=new Label();
 private List<CatalogEntry> catalog=List.of();
 private int pendingOperations;
 public ServerSettingsView(AppServices services,DocumentCodec codec){
  super(16);this.services=services;this.codec=codec;setId("server-settings-view");getStyleClass().add("dashboard-content");
  Label title=new Label("Configuración");title.getStyleClass().add("dashboard-title");
  Label subtitle=new Label("Selecciona el sistema que deseas administrar.");
  Button reports=button("Reportes",()->module(DocumentKind.REPORT)),quotes=button("Cotización",()->module(DocumentKind.QUOTATION));reports.setId("settings-reports");quotes.setId("settings-quotations");
  reports.getStyleClass().add("button-secondary");quotes.getStyleClass().add("button-secondary");
  status.setWrapText(true);status.managedProperty().bind(status.textProperty().isNotEmpty());status.visibleProperty().bind(status.managedProperty());status.getStyleClass().add("muted-label");
  getChildren().addAll(title,subtitle,new HBox(10,reports,quotes),status,body);VBox.setVgrow(body,Priority.ALWAYS);
  body.getChildren().add(new Label("Reportes: plantillas de formato.\nCotización: clientes, alcances y plantillas de formato."));
 }
 private static Button button(String text,Runnable action){Button button=AppIcons.button(text);button.setOnAction(e->action.run());return button;}
 private void module(DocumentKind kind){
  status.setText("");
  for(String id:List.of("settings-reports","settings-quotations")){
   var node=lookup("#"+id);if(node!=null){node.getStyleClass().remove("button-primary");if(id.equals(kind==DocumentKind.REPORT?"settings-reports":"settings-quotations"))node.getStyleClass().add("button-primary");}
  }
  TabPane tabs=new TabPane();tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
  if(kind==DocumentKind.QUOTATION){tabs.getTabs().addAll(new Tab("Clientes",catalogPanel(CatalogEntry.Kind.CLIENT)),new Tab("Alcances",catalogPanel(CatalogEntry.Kind.SCOPE)));}
  tabs.getTabs().add(new Tab("Plantillas",templatePanel(kind)));body.getChildren().setAll(tabs);VBox.setVgrow(tabs,Priority.ALWAYS);
 }
 private VBox catalogPanel(CatalogEntry.Kind kind){
  TextField search=new TextField();search.setPromptText("Buscar…");ListView<CatalogEntry> list=new ListView<>();list.setPrefHeight(320);list.setId("manage-"+kind.name().toLowerCase());
  list.setCellFactory(v->new ListCell<>(){protected void updateItem(CatalogEntry value,boolean empty){super.updateItem(value,empty);setText(empty||value==null?null:value.getText());}});
  TextArea text=new TextArea();text.setPromptText(kind==CatalogEntry.Kind.CLIENT?"Nombre del cliente":"Descripción del alcance");text.setWrapText(true);text.setPrefRowCount(3);
  TextField code=new TextField();code.setPromptText("Código del cliente (opcional)");code.setVisible(kind==CatalogEntry.Kind.CLIENT);code.setManaged(code.isVisible());
  Label hint=new Label("Los cambios afectan al catálogo compartido, no a los documentos ya guardados.");hint.setWrapText(true);
  Runnable filter=()->list.getItems().setAll(catalog.stream().filter(e->e.getKind()==kind&&CatalogEntry.normalize(e.getText()+" "+e.getCode()).contains(CatalogEntry.normalize(search.getText()))).sorted(Comparator.comparing(CatalogEntry::getText)).toList());
  Runnable reload=()->async(services::refreshCatalog,values->{catalog=values;filter.run();});
  search.textProperty().addListener((o,a,b)->filter.run());
  list.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b!=null){text.setText(b.getText());code.setText(b.getCode());}});
  Button create=button("Nuevo",()->{list.getSelectionModel().clearSelection();text.clear();code.clear();text.requestFocus();});
  Button save=button("Guardar",()->{
   CatalogEntry selected=list.getSelectionModel().getSelectedItem();CatalogEntry value=new CatalogEntry(kind,text.getText());value.setCode(code.getText());
   try{value.validate();}catch(Exception ex){status.setText(ex.getMessage());return;}
   if(catalog.stream().anyMatch(e->e.getKind()==kind&&CatalogEntry.normalize(e.getText()).equals(CatalogEntry.normalize(value.getText()))&&(selected==null||!e.id().equals(selected.id())))){status.setText("Ya existe un dato con ese nombre.");return;}
   if(selected==null)value.retainId(UUID.randomUUID().toString());
   async(()->{if(selected==null)return services.saveCatalogEntry(value);services.updateCatalogEntry(selected.id(),value);return false;},queued->{status.setText(queued?"Guardado en este equipo; pendiente de sincronización.":"Dato guardado.");reload.run();});
  });save.getStyleClass().add("button-primary");
  Button delete=button("Eliminar",()->{
   CatalogEntry selected=list.getSelectionModel().getSelectedItem();if(selected==null)return;
   if(!confirm("¿Eliminar «"+selected.getText()+"» del catálogo compartido?"))return;
   async(()->{services.deleteCatalogEntry(selected.id());return true;},done->{text.clear();code.clear();status.setText("Dato eliminado.");reload.run();});
  });delete.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());delete.getStyleClass().add("button-danger");
  VBox panel=new VBox(10,hint,search,list,text,code,new HBox(8,create,save,delete,button("Actualizar",reload)));panel.setStyle("-fx-padding: 16;");VBox.setVgrow(list,Priority.ALWAYS);reload.run();return panel;
 }
 private VBox templatePanel(DocumentKind kind){
  VBox panel=new VBox(10);panel.setStyle("-fx-padding: 16;");ListView<TemplateDefinition> list=new ListView<>();list.setId("manage-templates");
  list.setCellFactory(v->new ListCell<>(){protected void updateItem(TemplateDefinition value,boolean empty){super.updateItem(value,empty);setText(empty||value==null?null:value.getName());}});
  Runnable[] reload={null};
  reload[0]=()->async(()->services.listTemplates(kind),values->{
   List<TemplateDefinition> all=new ArrayList<>(values);TemplateDefinition defaults=kind==DocumentKind.REPORT?TemplateDefinition.defaults():TemplateDefinition.quotationDefaults();
   if(all.stream().noneMatch(t->t.getName().equals(defaults.getName())))all.addFirst(defaults);list.getItems().setAll(all);
  });
  Consumer<TemplateDefinition> edit=source->{
   String oldKey=source==null?null:source.storageName();
   TemplateDefinition value=source==null?(kind==DocumentKind.REPORT?TemplateDefinition.defaults():TemplateDefinition.quotationDefaults()):source;
   if(source==null)value.setName("Nueva plantilla");
   VBox editor=QuotationTemplateEditor.managementContent(value,codec,changed->{
    boolean duplicate=list.getItems().stream().anyMatch(t->t.storageName().equals(changed.storageName())&&!Objects.equals(t.storageName(),oldKey));
    if(duplicate){status.setText("Ya existe una plantilla con ese nombre.");return;}
    async(()->{services.saveTemplate(changed);if(oldKey!=null&&!oldKey.equals(changed.storageName())&&!isDefault(source))services.deleteTemplate(oldKey);return true;},done->{status.setText("Plantilla guardada.");panel.getChildren().setAll(templatePanel(kind));});
   },()->panel.getChildren().setAll(templatePanel(kind)));
   ScrollPane scroll=new ScrollPane(editor);scroll.setFitToWidth(true);panel.getChildren().setAll(scroll);VBox.setVgrow(scroll,Priority.ALWAYS);
  };
  Button update=button("Editar",()->{if(list.getSelectionModel().getSelectedItem()!=null)edit.accept(list.getSelectionModel().getSelectedItem());});update.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
  Button remove=button("Eliminar",()->{
   TemplateDefinition selected=list.getSelectionModel().getSelectedItem();if(selected==null)return;
   if(isDefault(selected)){status.setText("La plantilla predeterminada se conserva. Puedes crear otras plantillas.");return;}
   if(confirm("¿Eliminar la plantilla «"+selected.getName()+"»?"))async(()->{services.deleteTemplate(selected.storageName());return true;},done->{status.setText("Plantilla eliminada.");reload[0].run();});
  });remove.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());remove.getStyleClass().add("button-danger");
  panel.getChildren().setAll(new Label("Plantillas de formato"),list,new HBox(8,button("Nueva plantilla",()->edit.accept(null)),update,remove,button("Actualizar",reload[0])));VBox.setVgrow(list,Priority.ALWAYS);reload[0].run();return panel;
 }
 private boolean isDefault(TemplateDefinition template){return template.getName().equals("Formato predeterminado")||template.getName().equals("Cotización predeterminada");}
 private boolean confirm(String text){Alert alert=new Alert(Alert.AlertType.CONFIRMATION,text,ButtonType.YES,ButtonType.NO);alert.initOwner(getScene().getWindow());App.applyTheme(alert.getDialogPane());return alert.showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}
 @FunctionalInterface private interface Operation<T>{T run()throws Exception;}
 private <T> void async(Operation<T> operation,Consumer<T> success){
  pendingOperations++;setDisable(true);Task<T> task=new Task<>(){protected T call()throws Exception{return operation.run();}};
  task.setOnSucceeded(e->{setDisable(--pendingOperations>0);success.accept(task.getValue());});task.setOnFailed(e->{setDisable(--pendingOperations>0);status.setText("No se pudo completar la operación: "+task.getException().getMessage());});Thread.ofVirtual().start(task);
 }
}
