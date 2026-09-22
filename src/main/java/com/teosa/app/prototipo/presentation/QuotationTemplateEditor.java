package com.teosa.app.prototipo.presentation;
import com.teosa.app.prototipo.application.AppServices;
import com.teosa.app.prototipo.application.port.DocumentCodec;
import com.teosa.app.prototipo.domain.*;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import java.util.*;
import java.util.function.Consumer;

public final class QuotationTemplateEditor {
    private QuotationTemplateEditor(){}
    public static VBox content(TemplateDefinition source,Quotation document,DocumentCodec codec,AppServices services,Consumer<TemplateDefinition> onApply,Runnable onCancel){
        return content(source,document,codec,services,onApply,onCancel,false,t->{},()->{},source.getName());
    }
    public static VBox managementContent(TemplateDefinition source,DocumentCodec codec,Consumer<TemplateDefinition> onSave,Runnable onCancel){
        return content(source,new Quotation(),codec,null,onSave,onCancel,true,t->{},()->{},source.getName());
    }
    public static VBox content(TemplateDefinition source,Quotation document,DocumentCodec codec,AppServices services,Consumer<TemplateDefinition> onApply,Runnable onCancel,Consumer<TemplateDefinition> onSaved,Runnable onDeleted,String storedName){
        return content(source,document,codec,services,onApply,onCancel,false,onSaved,onDeleted,storedName);
    }
    private static VBox content(TemplateDefinition source,Quotation document,DocumentCodec codec,AppServices services,Consumer<TemplateDefinition> onApply,Runnable onCancel,boolean management,Consumer<TemplateDefinition> onSaved,Runnable onDeleted,String storedName){
        TemplateDefinition template=codec.copy(source,TemplateDefinition.class);
        // Preserve older templates' total color before making table colors independent.
        if(template.getKind()==DocumentKind.QUOTATION)template.setTotalBackgroundColor(template.getTotalBackgroundColor());
        String[] persistedName={storedName};
        VBox body=new VBox(10);body.setPadding(new Insets(12));
        Runnable publish=()->{if(!management){ViewportPosition position=ViewportPosition.captureAncestor(body);onApply.accept(template);position.restore();}};
        Controls ui=new Controls(publish);
        TextField name=ui.text(template.getName(),template::setName);CheckBox presets=new CheckBox("Guardar los valores actuales como preestablecidos");
        Label result=new Label();result.setWrapText(true);result.managedProperty().bind(result.textProperty().isNotEmpty());result.visibleProperty().bind(result.managedProperty());
        Button save=AppIcons.button("Guardar plantilla"),delete=AppIcons.button("Eliminar plantilla guardada");
        save.setOnAction(e->{
            if(name.getText().isBlank()){result.setText("Escribe el nombre de la plantilla.");return;}
            if(presets.isSelected()){template.getPresetValues().clear();template.getPresetValues().putAll(document.getValues());template.getPresetValues().remove("folio");}
            template.setName(name.getText().trim());template.setLastUsedAt(System.currentTimeMillis());TemplateDefinition snapshot=codec.copy(template,TemplateDefinition.class);
            save.setDisable(true);delete.setDisable(true);
            Task<Void> task=new Task<>(){protected Void call()throws Exception{services.saveTemplate(snapshot);return null;}};
            task.setOnSucceeded(ev->{save.setDisable(false);delete.setDisable(false);persistedName[0]=snapshot.getName();onSaved.accept(snapshot);result.setText("Plantilla guardada. Los cambios ya se muestran en esta cotización.");});
            task.setOnFailed(ev->{save.setDisable(false);delete.setDisable(false);result.setText(task.getException().getMessage());});Thread.ofVirtual().start(task);
        });
        delete.setOnAction(e->{
            if(persistedName[0].equals("Cotización predeterminada")){result.setText("La plantilla predeterminada se conserva.");return;}
            Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar la plantilla guardada «"+persistedName[0]+"»?",ButtonType.YES,ButtonType.NO);
            if(confirm.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
            save.setDisable(true);delete.setDisable(true);String key="quotation:"+persistedName[0];
            Task<Void> task=new Task<>(){protected Void call()throws Exception{services.deleteTemplate(key);return null;}};
            task.setOnSucceeded(ev->{save.setDisable(false);delete.setDisable(false);result.setText("Plantilla eliminada.");onDeleted.run();});
            task.setOnFailed(ev->{save.setDisable(false);delete.setDisable(false);result.setText(task.getException().getMessage());});Thread.ofVirtual().start(task);
        });
        body.getChildren().addAll(new Label("Nombre de la plantilla"),name);
        if(!management)body.getChildren().addAll(presets,new VBox(8,save,delete));
        body.getChildren().add(result);
        VBox headers=HeaderFieldsDialog.content(template.getHeaderLines(),line->ui.styleEditor(line.getStyle()),publish);
        ComboBox<String> alignment=new ComboBox<>(FXCollections.observableArrayList("LEFT","CENTER","RIGHT"));alignment.setValue(template.getHeaderTextAlignment());alignment.setOnAction(e->{template.setHeaderTextAlignment(alignment.getValue());publish.run();});
        ComboBox<String> layout=new ComboBox<>(FXCollections.observableArrayList("SIDE_BY_SIDE","STACKED"));layout.setValue(template.getHeaderLayout());layout.setOnAction(e->{template.setHeaderLayout(layout.getValue());publish.run();});
        alignment.setConverter(labels(Map.of("LEFT","Izquierda","CENTER","Centro","RIGHT","Derecha")));
        layout.setConverter(labels(Map.of("SIDE_BY_SIDE","Logo al lado del texto","STACKED","Logo arriba del texto")));
        Spinner<Double> width=new Spinner<>(50.0,200.0,Math.min(200,template.getHeaderImageWidth()),5.0);
        width.valueProperty().addListener((o,a,v)->{template.setHeaderImageWidth(v);publish.run();});
        Spinner<Double> gap=new Spinner<>(0.0,60.0,Math.min(60,template.getHeaderGap()),2.0);gap.valueProperty().addListener((o,a,v)->{template.setHeaderGap(v);publish.run();});
        body.getChildren().add(section("Encabezado",new VBox(8,headers,new Label("Alineación"),alignment,new Label("Distribución"),layout,new Label("Ancho del logotipo"),width,new Label("Separación"),gap)));
        VBox fields=new VBox(8);
        Consumer<FieldDefinition> addFieldEditor=def->{
            TextField label=ui.text(def.getLabel(),def::setLabel);label.setId("format-field-"+def.getKey());
            VBox card=new VBox(5,label);
            if(def.isCustom()){
                Button remove=AppIcons.button("Eliminar");
                remove.setOnAction(e->{template.getFields().remove(def.getKey());fields.getChildren().remove(card);publish.run();});
                card.getChildren().add(remove);
            }
            fields.getChildren().add(card);
        };
        boolean quotationFormat=template.getKind()==DocumentKind.QUOTATION;
        template.orderedFields().stream().filter(def->!quotationFormat||!def.getKey().equals("folio")).forEach(addFieldEditor);
        ColorPicker fieldColor=ui.color(template.fieldsBackgroundColor(),template::setFieldsBackgroundColor);
        fieldColor.setId("format-fields-color");
        VBox fieldSettings=new VBox(8);
        FieldDefinition folio=quotationFormat?template.getFields().get("folio"):null;
        if(folio!=null){
            TextField folioLabel=ui.text(folio.getLabel(),folio::setLabel);folioLabel.setId("format-field-folio");
            fieldSettings.getChildren().addAll(new Label("Título de cotización"),folioLabel,new Separator());
        }
        fieldSettings.getChildren().addAll(new Label(quotationFormat?"Color de fondo de los demás campos":"Color de todos los campos"),fieldColor,fields);
        body.getChildren().add(section("Campos del formulario",fieldSettings));
        if(template.getKind()==DocumentKind.QUOTATION){
            ColorPicker tableColor=ui.color(template.getSectionBackgroundColor(),template::setSectionBackgroundColor);tableColor.setId("format-table-color");
            ColorPicker totalColor=ui.color(template.getTotalBackgroundColor(),template::setTotalBackgroundColor);totalColor.setId("format-total-color");
            VBox table=subsection("Tabla de partidas","content-table",
                    new Label("Título de la columna de descripción"),ui.text(template.getSection1Title(),template::setSection1Title),
                    new Label("Color de fondo de los encabezados de la tabla"),tableColor);
            VBox notes=subsection("Alcances y notas","content-notes",
                    new Label("Título de alcances"),ui.text(template.getSection2Title(),template::setSection2Title),
                    new Label("Título de notas"),ui.text(template.getSection3Title(),template::setSection3Title));
            VBox total=subsection("Total","content-total",new Label("Color de fondo de la fila TOTAL"),totalColor);
            VBox text=subsection("Texto del documento","content-text",ui.styleEditor(template.getPhotoCommentStyle()));
            body.getChildren().add(section("Contenido",new VBox(14,table,new Separator(),notes,new Separator(),total,new Separator(),text)));
            TextField address=ui.text(template.getQuotationFooterAddress(),template::setQuotationFooterAddress);address.setId("format-footer-address");
            TextField contact=ui.text(template.getQuotationFooterContact(),template::setQuotationFooterContact);contact.setId("format-footer-contact");
            Label footerHelp=new Label("Dos renglones centrados al pie de cada página.");footerHelp.setWrapText(true);
            body.getChildren().add(section("Pie de página",new VBox(8,footerHelp,new Label("Dirección"),address,new Label("RFC, teléfono y correo"),contact)));
        }else{
            body.getChildren().add(section("Títulos y color de secciones",new VBox(8,ui.text(template.getSection1Title(),template::setSection1Title),ui.text(template.getSection2Title(),template::setSection2Title),ui.text(template.getSection3Title(),template::setSection3Title),ui.color(template.getSectionBackgroundColor(),template::setSectionBackgroundColor))));
            body.getChildren().add(section("Texto del documento",ui.styleEditor(template.getPhotoCommentStyle())));
        }
        if(management){
            VBox presetFields=new VBox(8);
            java.util.Set<String> keys=new java.util.LinkedHashSet<>(template.getPresetValues().keySet());
            keys.addAll(template.getFields().keySet());if(template.getKind()==DocumentKind.REPORT)keys.add("cliente");
            for(String key:keys){FieldDefinition def=template.getFields().get(key);presetFields.getChildren().addAll(new Label(def==null?key:def.getLabel()),ui.text(template.getPresetValues().getOrDefault(key,""),value->template.getPresetValues().put(key,value)));}
            body.getChildren().add(section("Valores preestablecidos",presetFields));
        }
        if(management){
            Button apply=AppIcons.button("Guardar plantilla"),cancel=AppIcons.button("Cancelar");
            apply.getStyleClass().add("button-primary");
            apply.setOnAction(e->{if(template.getName().isBlank()){result.setText("Escribe el nombre de la plantilla.");return;}template.setName(template.getName().trim());template.setLastUsedAt(System.currentTimeMillis());onApply.accept(template);});
            cancel.setOnAction(e->onCancel.run());body.getChildren().add(new HBox(8,apply,cancel));
        }else{
            Label live=new Label("Los cambios se muestran automáticamente en la vista previa.");live.setWrapText(true);
            Button close=AppIcons.button("Cerrar personalización");close.setOnAction(e->onCancel.run());body.getChildren().addAll(live,close);
        }
        save.getStyleClass().add("button-primary");delete.getStyleClass().add("button-danger");
        save.setMaxWidth(Double.MAX_VALUE);delete.setMaxWidth(Double.MAX_VALUE);
        return body;
    }
    private static VBox subsection(String title,String id,javafx.scene.Node... controls){
        Label heading=new Label(title);heading.getStyleClass().add("section-title");
        VBox group=new VBox(8,heading);group.setId(id);group.getChildren().addAll(controls);return group;
    }
    private static javafx.util.StringConverter<String> labels(Map<String,String> labels){return new javafx.util.StringConverter<>(){public String toString(String s){return labels.getOrDefault(s,s);}public String fromString(String s){return s;}};}
    private static TitledPane section(String title,javafx.scene.Node content){TitledPane pane=new TitledPane(title,content);pane.setExpanded(false);pane.setAnimated(false);return pane;}
    private static final class Controls {
        private final Runnable changed;
        Controls(Runnable changed){this.changed=changed;}
        TextField text(String value,Consumer<String> update){TextField field=new TextField(value);field.textProperty().addListener((o,a,v)->{update.accept(v);changed.run();});return field;}
        ColorPicker color(String value,Consumer<String> update){ColorPicker picker=new ColorPicker(Color.web(value));picker.valueProperty().addListener((o,a,c)->{update.accept(String.format("#%02x%02x%02x",Math.round(c.getRed()*255),Math.round(c.getGreen()*255),Math.round(c.getBlue()*255)));changed.run();});return picker;}
        VBox styleEditor(TextStyle style){
            ComboBox<String> family=new ComboBox<>(FXCollections.observableArrayList("Arial","Helvetica","Times New Roman","Courier New"));family.setValue(style.getFontFamily());family.setOnAction(e->{style.setFontFamily(family.getValue());changed.run();});
            Spinner<Double> size=new Spinner<>(7.0,28.0,Math.max(7,Math.min(28,style.getFontSize())),1.0);size.valueProperty().addListener((o,a,v)->{style.setFontSize(v);changed.run();});
            CheckBox bold=new CheckBox("Negrita"),italic=new CheckBox("Cursiva");bold.setSelected(style.isBold());italic.setSelected(style.isItalic());
            bold.selectedProperty().addListener((o,a,v)->{style.setBold(v);changed.run();});italic.selectedProperty().addListener((o,a,v)->{style.setItalic(v);changed.run();});
            return new VBox(5,new FlowPane(8,8,family,size,color(style.getColor(),style::setColor)),new HBox(8,bold,italic));
        }
    }
}
