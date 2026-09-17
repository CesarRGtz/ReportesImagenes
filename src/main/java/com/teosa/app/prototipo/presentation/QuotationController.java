package com.teosa.app.prototipo.presentation;

import com.teosa.app.prototipo.App;
import com.teosa.app.prototipo.application.AppServices;
import com.teosa.app.prototipo.application.port.*;
import com.teosa.app.prototipo.domain.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import static com.teosa.app.prototipo.presentation.DesktopChrome.label;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

public final class QuotationController {
    private final AppServices services;
    private final DocumentCodec codec;
    private final DocumentOutput output;
    private final BorderPane root=new BorderPane();
    private final VBox form=new VBox(), linesBox=new VBox(10), preview=new VBox(16);
    private final ScrollPane formScroll=new ScrollPane(form),previewScroll=new ScrollPane(preview);
    private final Map<String,Label> fieldLabels=new LinkedHashMap<>();
    private final Map<String,javafx.scene.Node> fieldInputs=new LinkedHashMap<>();
    private VBox details;
    private final Label status=new Label(), totals=new Label(), previewStatus=new Label();
    private final ComboBox<TemplateDefinition> templates=new ComboBox<>();
    private final PauseTransition debounce=new PauseTransition(Duration.millis(450));
    private final Set<String> invalid=new HashSet<>();
    private Quotation quotation=new Quotation();
    private TemplateDefinition template=TemplateDefinition.quotationDefaults();
    private String id=UUID.randomUUID().toString(), savedFingerprint;
    private int version, previewRevision;
    private List<CatalogEntry> catalog=List.of();
    private boolean loadingCatalog;
    private final javafx.animation.Timeline catalogRefresh=new javafx.animation.Timeline();
    private boolean saving, rendering, previewAgain, loadingTemplates, disposed;
    private final Consumer<String> statusListener=s->Platform.runLater(()->status.setText(s));

    public QuotationController(AppServices services,DocumentCodec codec,DocumentOutput output) {
        this.services=services;this.codec=codec;this.output=output;
        root.getStyleClass().add("app-shell");
        root.setTop(DesktopChrome.header("Cotizaciones TEOSA","Gestión de servicios y cotizaciones"));
        templates.setConverter(new StringConverter<>() {
            public String toString(TemplateDefinition t){return t==null?"":t.getName();}
            public TemplateDefinition fromString(String s){return null;}
        });
        templates.setMaxWidth(Double.MAX_VALUE);
        templates.setOnShowing(e->loadTemplates());
        templates.setOnAction(e->{
            TemplateDefinition selected=templates.getValue();
            if(selected==null || loadingTemplates || !numbersValid())return;
            root.setRight(null);
            template=codec.copy(selected,TemplateDefinition.class);
            template.getPresetValues().forEach(quotation::setValue);
            rebuildForm();changed();
        });
        form.getStyleClass().add("sidebar-content");
        status.getStyleClass().add("connection-status");status.setWrapText(true);
        totals.getStyleClass().add("field-label");totals.setWrapText(true);
        formScroll.setFitToWidth(true);
        formScroll.setMinWidth(440);formScroll.setPrefWidth(480);formScroll.getStyleClass().add("form-scroll");
        preview.setAlignment(Pos.TOP_CENTER);preview.setPadding(new Insets(68,0,30,0));
        preview.getStyleClass().add("preview-canvas");
        previewScroll.setFitToWidth(true);previewScroll.getStyleClass().add("preview-scroll");
        HBox actions=new HBox(7,styledButton("Generar PDF","button-primary",()->withCatalogPrompt(this::exportPdf)),
                styledButton("Guardar cotización","button-secondary",()->withCatalogPrompt(this::save)),styledButton("Imprimir","button-success",()->withCatalogPrompt(this::print)));
        actions.setAlignment(Pos.CENTER_LEFT);actions.setMaxSize(Region.USE_PREF_SIZE,Region.USE_PREF_SIZE);
        actions.getStyleClass().add("floating-action-bar");
        StackPane pages=new StackPane(previewScroll,actions);StackPane.setAlignment(actions,Pos.TOP_LEFT);
        StackPane.setMargin(actions,new Insets(14,0,0,14));VBox.setVgrow(pages,Priority.ALWAYS);
        previewStatus.getStyleClass().add("muted-label");previewStatus.setWrapText(true);
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        HBox previewHeading=new HBox(8,label("Previsualización del documento","preview-heading"),label("PDF","preview-badge"),spacer,previewStatus);
        previewHeading.setAlignment(Pos.CENTER_LEFT);
        VBox previewPanel=new VBox(previewHeading,pages);previewPanel.setMinWidth(0);
        previewPanel.getStyleClass().add("preview-panel");HBox.setHgrow(previewPanel,Priority.ALWAYS);
        HBox workspace=new HBox(formScroll,previewPanel);workspace.getStyleClass().add("workspace");root.setCenter(workspace);
        debounce.setOnFinished(e->refreshPreview());
        services.addStatusListener(statusListener);
        rebuildForm();savedFingerprint=fingerprint();loadTemplates();loadCatalog();changed();
        catalogRefresh.getKeyFrames().add(new javafx.animation.KeyFrame(Duration.seconds(5),e->loadCatalog()));
        catalogRefresh.setCycleCount(javafx.animation.Animation.INDEFINITE);catalogRefresh.play();
    }
    private void loadCatalog(){
        if(loadingCatalog||disposed)return;loadingCatalog=true;
        background(services::listCatalog,list->{catalog=list;loadingCatalog=false;},ex->{loadingCatalog=false;});
    }
    private void catalogActionFinished(){saving=false;root.getCenter().setDisable(false);}
    private void withCatalogPrompt(Runnable action){
        if(saving||!valid())return;
        saving=true;root.getCenter().setDisable(true);
        background(services::refreshCatalog,known->{
            catalog=known;List<CatalogEntry> fresh=NewCatalogData.find(quotation,known);
            if(fresh.isEmpty()){catalogActionFinished();action.run();return;}
            var choice=NewCatalogDialog.ask(root.getScene().getWindow(),fresh);
            if(choice.isEmpty()){catalogActionFinished();return;}
            if(choice.get().isEmpty()){catalogActionFinished();action.run();return;}
            choice.get().forEach(entry->entry.retainId(UUID.randomUUID().toString()));
            background(()->services.saveCatalogEntries(choice.get()),queued->{
                loadCatalog();catalogActionFinished();
                if(queued)message("Datos guardados en este equipo","Se compartirán cuando el servidor esté disponible.");
                action.run();
            },ex->{catalogActionFinished();message("No se pudieron guardar los datos nuevos",ex.getMessage());});
        },ex->{catalogActionFinished();message("No se pudo consultar el catálogo",ex.getMessage());});
    }
    public void refreshSharedTemplates(){loadTemplates();loadCatalog();}
    public Parent view(){return root;}
    private Button button(String text,Runnable action){Button b=AppIcons.button(text);b.setOnAction(e->action.run());return b;}
    private Button styledButton(String text,String style,Runnable action){Button b=button(text,action);b.getStyleClass().add(style);return b;}
    private VBox card(String title,String help){
        Label description=label(help,"section-help");description.setWrapText(true);
        VBox box=new VBox(label(title,"section-title"),description);box.getStyleClass().add("form-card");return box;
    }
    private TextField field(String value,Consumer<String> update){
        TextField f=new TextField(value);f.textProperty().addListener((o,a,v)->{update.accept(v);changed();});return f;
    }
    private TextArea area(String value,Consumer<String> update){
        TextArea a=new TextArea(value);a.setWrapText(true);a.setPrefRowCount(3);
        a.textProperty().addListener((o,p,v)->{update.accept(v);changed();});return a;
    }
    private void rebuildForm(){
        form.getChildren().clear();fieldLabels.clear();fieldInputs.clear();invalid.clear();
        Label help=label("Completa los datos y organiza las partidas de tu cotización.","section-help");help.setWrapText(true);
        VBox heading=new VBox(4,label("Crear cotización","sidebar-title"),help);heading.getStyleClass().add("sidebar-heading");
        Button history=styledButton("Importar / Historial","button-secondary",this::history);history.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(history,Priority.ALWAYS);
        HBox documentActions=new HBox(8,history,button("Nueva",this::newDocument));
        VBox format=card("Plantilla y formato","Aplica una configuración guardada o personaliza este documento.");
        Button customize=styledButton("Personalizar formato","button-secondary",this::customize);customize.setMaxWidth(Double.MAX_VALUE);
        format.getChildren().addAll(label("Plantilla","field-label"),templates,customize);
        details=card("Datos del servicio","Información que aparecerá en el encabezado y la tabla de la cotización.");
        form.getChildren().addAll(heading,status,documentActions,format,details);
        template.orderedFields().forEach(this::addDetailField);
        TextArea introduction=area(quotation.getIntroduction(),quotation::setIntroduction);introduction.setId("quotation-introduction");
        int introductionIndex=fieldInputs.containsKey("folio")?details.getChildren().indexOf(fieldInputs.get("folio"))+1:2;
        details.getChildren().addAll(introductionIndex,List.of(label("Texto de presentación","field-label"),introduction));
        VBox items=card("Partidas y alcances","Agrega los servicios, cantidades y costos que vas a cotizar.");
        Button add=styledButton("Agregar partida","button-primary",()->{
            if(!numbersValid())return;
            QuotationLine line=new QuotationLine();line.setCode("P"+String.format("%02d",quotation.getLines().size()+1));
            quotation.getLines().add(line);rebuildLines();changed();
        });add.setMaxWidth(Double.MAX_VALUE);items.getChildren().addAll(add,linesBox);
        VBox notes=card("Notas e importes","Revisa las condiciones del servicio y el resumen de la cotización.");
        notes.getChildren().addAll(label(template.getSection3Title(),"field-label"),area(quotation.getNotes(),quotation::setNotes));
        form.getChildren().addAll(items,notes);
        TextField tax=number(quotation.getTaxRate().movePointRight(2),"IVA",v->{
            if(v.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException();quotation.setTaxRate(v.movePointLeft(2));
        },false);
        notes.getChildren().addAll(label("IVA (%)","field-label"),tax,totals);rebuildLines();
    }
    private void addDetailField(FieldDefinition def){
            String key=def.getKey();
            Label label=label(def.getLabel(),"field-label");fieldLabels.put(key,label);
            details.getChildren().add(label);
            if(key.equals("pago")){
                ComboBox<String> payment=new ComboBox<>(FXCollections.observableArrayList(Quotation.PAYMENT_OPTIONS));
                payment.setValue(quotation.value(key));payment.setMaxWidth(Double.MAX_VALUE);
                payment.setOnAction(e->{quotation.setValue(key,payment.getValue());changed();});details.getChildren().add(payment);
            }else if(key.equals("fecha")){
                DatePicker date=new DatePicker();
                date.setConverter(new StringConverter<>(){
                    public String toString(LocalDate value){return value==null?"":value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));}
                    public LocalDate fromString(String value){return value==null||value.isBlank()?null:LocalDate.parse(value,DateTimeFormatter.ofPattern("dd/MM/yyyy"));}
                });
                try{date.setValue(LocalDate.parse(quotation.value(key),DateTimeFormatter.ofPattern("dd/MM/yyyy")));}catch(Exception ignored){}
                date.setMaxWidth(Double.MAX_VALUE);date.setEditable(false);date.valueProperty().addListener((o,a,v)->{quotation.setValue(key,v==null?"":v.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));changed();});
                details.getChildren().add(date);
            }else {
                TextField input=field(quotation.value(key),v->quotation.setValue(key,v));input.setId("quotation-"+key);details.getChildren().add(input);
                if(key.equals("cliente")){
                    CatalogAutocomplete.attach(input,CatalogEntry.Kind.CLIENT,()->catalog);

                }
            }
            fieldInputs.put(key,details.getChildren().getLast());
    }
    private void updateDetailFields(){
        ViewportPosition position=ViewportPosition.capture(formScroll);
        for(String key:List.copyOf(fieldLabels.keySet()))if(!template.getFields().containsKey(key)){
            details.getChildren().removeAll(fieldLabels.remove(key),fieldInputs.remove(key));
        }
        for(FieldDefinition def:template.orderedFields()){
            if(!fieldLabels.containsKey(def.getKey())){
                int index=4+fieldLabels.size()*2;
                addDetailField(def);
                Label label=fieldLabels.get(def.getKey());javafx.scene.Node input=fieldInputs.get(def.getKey());
                details.getChildren().removeAll(label,input);details.getChildren().addAll(index,List.of(label,input));
            }else fieldLabels.get(def.getKey()).setText(def.getLabel());
        }
        position.restore();
    }
    private TextField number(BigDecimal initial,String key,Consumer<BigDecimal> setter,boolean quantity){
        TextField f=new TextField(initial.stripTrailingZeros().toPlainString());
        f.textProperty().addListener((o,a,s)->{
            try{
                BigDecimal n=new BigDecimal(s.trim());
                if(n.signum()<0 || (quantity&&(n.signum()==0 || n.stripTrailingZeros().scale()>0)))throw new IllegalArgumentException();
                setter.accept(n);invalid.remove(key);f.setStyle("");
            }catch(Exception ex){invalid.add(key);f.setStyle("-fx-border-color:#dc2626;");}
            changed();
        });return f;
    }
    private void rebuildLines(){
        linesBox.getChildren().clear();invalid.removeIf(s->s.startsWith("partida-"));
        for(QuotationLine line:quotation.getLines()){
            String key="partida-"+System.identityHashCode(line);
            TextField unitPrice=number(line.getUnitPrice(),key+"-precio",line::setUnitPrice,false);
            unitPrice.setEditable(!line.isScopeBreakdown());unitPrice.setId(key+"-unit-price");
            CheckBox breakdown=new CheckBox("Desglosar costo por alcance");breakdown.setSelected(line.isScopeBreakdown());breakdown.setId(key+"-breakdown");
            breakdown.setOnAction(e->{if(!numbersValid()){breakdown.setSelected(line.isScopeBreakdown());return;}line.setScopeBreakdown(breakdown.isSelected());rebuildLines();changed();});
            VBox scopes=new VBox(8);
            for(QuotationScope scope:List.copyOf(line.getScopes()))appendScopeEditor(line,scope,scopes,unitPrice,key);
            appendScopeEditor(line,new QuotationScope(),scopes,unitPrice,key);
            VBox card=new VBox(5,new Label("Clave"),field(line.getCode(),line::setCode),
                    new Label("Descripción"),area(line.getDescription(),line::setDescription),
                    new Label("Cantidad"),number(line.getQuantity(),key+"-cantidad",line::setQuantity,true),
                    new Label(line.isScopeBreakdown()?"Costo unitario (suma de alcances)":"Costo unitario"),unitPrice,
                    new Label(template.getSection2Title()),breakdown,scopes);
            Button up=button("Subir",()->moveLine(line,-1)),down=button("Bajar",()->moveLine(line,1));
            card.getChildren().add(new HBox(6,up,down,button("Eliminar partida",()->{
                if(!numbersValid())return;
                quotation.getLines().remove(line);rebuildLines();changed();
            })));
            card.getStyleClass().add("editor-card");linesBox.getChildren().add(card);
        }
    }
    private void appendScopeEditor(QuotationLine line,QuotationScope scope,VBox scopes,TextField unitPrice,String key){
        boolean[] active={line.getScopes().contains(scope)};
        String scopeKey=key+"-scope-"+line.getScopes().indexOf(scope);
        Label code=label(active[0]?QuotationScope.code(line.getScopes().indexOf(scope)):"Nuevo alcance","field-label");
        TextArea description=new TextArea(scope.getDescription());description.setWrapText(true);description.setPrefRowCount(2);description.setPromptText("Escribe para añadir un alcance…");description.setId(active[0]?scopeKey+"-text":key+"-scope-draft");
        if(!active[0])description.getStyleClass().add("new-scope-input");
        CatalogAutocomplete.attach(description,CatalogEntry.Kind.SCOPE,()->catalog);
        VBox box=new VBox(4,code,description);
        VBox costBox=new VBox();costBox.setVisible(active[0]);costBox.setManaged(active[0]);
        if(line.isScopeBreakdown()){
            TextField cost=number(scope.getCost(),key+"-scope-cost-"+System.identityHashCode(scope),value->{scope.setCost(value);unitPrice.setText(line.getUnitPrice().toPlainString());},false);cost.setId(scopeKey+"-cost");
            costBox.getChildren().addAll(label("Costo del alcance (por unidad)","field-label"),cost);
        }
        Button remove=button("Quitar",()->{if(!numbersValid())return;line.getScopes().remove(scope);rebuildLines();changed();});remove.setVisible(active[0]);remove.setManaged(active[0]);
        box.getChildren().addAll(costBox,remove);scopes.getChildren().add(box);
        description.textProperty().addListener((o,a,value)->{
            scope.setDescription(value);
            if(!active[0]&&!value.isBlank()){
                description.getStyleClass().remove("new-scope-input");
                active[0]=true;line.getScopes().add(scope);int index=line.getScopes().indexOf(scope);
                code.setText(QuotationScope.code(index));description.setId(key+"-scope-"+index+"-text");
                if(!costBox.getChildren().isEmpty())costBox.getChildren().getLast().setId(key+"-scope-"+index+"-cost");
                costBox.setVisible(true);costBox.setManaged(true);remove.setVisible(true);remove.setManaged(true);
                appendScopeEditor(line,new QuotationScope(),scopes,unitPrice,key);
            }
            changed();
        });
    }
    private void moveLine(QuotationLine line,int direction){
        if(!numbersValid())return;
        int old=quotation.getLines().indexOf(line),next=old+direction;
        if(next>=0 && next<quotation.getLines().size()){Collections.swap(quotation.getLines(),old,next);rebuildLines();changed();}
    }
    private String fingerprint(){return codec.toJson(quotation)+codec.toJson(template);}
    private void changed(){
        totals.setText("Subtotal: "+quotation.subtotal()+"   IVA: "+quotation.tax()+"   Total: "+quotation.total());
        previewRevision++;debounce.playFromStart();
    }
    private void refreshPreview(){
        if(disposed)return;
        if(!invalid.isEmpty()){previewStatus.setText("Revisa los números marcados en rojo. La vista conserva los últimos valores válidos.");return;}
        if(rendering){previewAgain=true;return;}
        rendering=true;int revision=previewRevision;
        Quotation copy=codec.copy(quotation,Quotation.class);TemplateDefinition style=codec.copy(template,TemplateDefinition.class);
        previewStatus.setText("Actualizando vista previa...");
        background(()->output.preview(copy,style),pages->{
            rendering=false;
            if(revision==previewRevision){
                ViewportPosition position=ViewportPosition.capture(previewScroll);
                for(int i=0;i<pages.size();i++){
                    ImageView image;
                    if(i<preview.getChildren().size())image=(ImageView)preview.getChildren().get(i);
                    else{image=new ImageView();image.setFitWidth(612);image.setPreserveRatio(true);preview.getChildren().add(image);}
                    image.setImage(new Image(new ByteArrayInputStream(pages.get(i))));
                }
                if(preview.getChildren().size()>pages.size())preview.getChildren().remove(pages.size(),preview.getChildren().size());
                position.restore();
                previewStatus.setText(pages.size()+" página(s) · Vista del PDF");
            }
            if(previewAgain || revision!=previewRevision){previewAgain=false;refreshPreview();}
        },ex->{rendering=false;previewStatus.setText("No se pudo actualizar la vista: "+ex.getMessage());});
    }
    private ReportSnapshot snapshot(){
        ReportSnapshot s=new ReportSnapshot();s.setReportId(id);s.setVersion(version);
        s.setQuotation(codec.copy(quotation,Quotation.class));s.setTemplate(codec.copy(template,TemplateDefinition.class));return s;
    }
    private boolean valid(){
        try{if(!invalid.isEmpty())throw new IllegalArgumentException("Revisa los números marcados en rojo.");quotation.validate();AmountInWords.pesos(quotation.total());return true;}
        catch(Exception ex){message("Revisa la cotización",ex.getMessage());return false;}
    }
    private void save(){
        if(saving || !valid())return;
        saving=true;ReportSnapshot snapshot=snapshot();String fingerprint=fingerprint();status.setText("Guardando...");
        background(()->services.saveReport(snapshot),response->{
            saving=false;id=response.getReportId();if(response.getVersion()>0)version=response.getVersion();savedFingerprint=fingerprint;
            message(response.isQueued()?"Guardada en este equipo":"Cotización guardada",response.getMessage());
        },ex->{saving=false;message("No se pudo guardar",ex.getMessage());});
    }
    public boolean confirmClose(){return confirmLeave(false);}
    public boolean confirmReturnHome(){return confirmLeave(true);}
    private boolean confirmLeave(boolean returnHome){
        if(saving){message("Guardado en curso","Espera a que termine el guardado.");return false;}
        return (invalid.isEmpty() && Objects.equals(savedFingerprint,fingerprint())) || confirm("Cambios sin guardar",returnHome ? "¿Deseas volver al menú principal sin guardar la cotización?" : "¿Descartar los cambios de la cotización?");
    }
    private void newDocument(){
        if(!confirmClose())return;
        root.setRight(null);
        quotation=new Quotation();template.getPresetValues().forEach(quotation::setValue);id=UUID.randomUUID().toString();version=0;
        rebuildForm();savedFingerprint=fingerprint();changed();
    }
    private void loadTemplates(){
        background(()->services.listTemplates(DocumentKind.QUOTATION),list->{
            loadingTemplates=true;
            templates.getItems().setAll(list);
            if(list.stream().noneMatch(t->t.getName().equals("Cotización predeterminada")))templates.getItems().addFirst(TemplateDefinition.quotationDefaults());
            TemplateDefinition selected=templates.getItems().stream().filter(t->t.getName().equals(template.getName())).findFirst().orElse(template);
            if(!templates.getItems().contains(selected))templates.getItems().add(selected);
            templates.setValue(selected);loadingTemplates=false;
        },ex->{loadingTemplates=true;templates.getItems().setAll(template);templates.setValue(template);loadingTemplates=false;});
    }
    private void customize(){
        if(root.getRight()!=null){root.setRight(null);return;}
        if(!numbersValid())return;
        VBox title=new VBox(1,label("Personalizar formato","drawer-title"),label("Diseño, campos y plantillas","brand-subtitle"));
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        HBox header=new HBox(8,title,spacer,button("Cerrar",()->root.setRight(null)));
        header.setAlignment(Pos.CENTER_LEFT);header.getStyleClass().add("drawer-header");
        VBox content=QuotationTemplateEditor.content(template,quotation,codec,services,edited->{
            template=edited;updateDetailFields();
            changed();
        },()->root.setRight(null));
        content.getStyleClass().add("drawer-content");
        ScrollPane scroll=new ScrollPane(content);scroll.setFitToWidth(true);scroll.getStyleClass().add("drawer-scroll");
        VBox.setVgrow(scroll,Priority.ALWAYS);
        VBox drawer=new VBox(header,scroll);drawer.setMinWidth(410);drawer.setPrefWidth(440);
        drawer.setId("quotation-customization");drawer.getStyleClass().add("customization-drawer");root.setRight(drawer);
    }
    private void history(){
        if(saving)return;
        Dialog<ReportSnapshot> dialog=new Dialog<>();dialog.initOwner(root.getScene().getWindow());App.applyTheme(dialog.getDialogPane());
        dialog.setTitle("Historial de cotizaciones");dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        TextField query=new TextField();query.setPromptText("Cliente, fecha, servicio, folio o SP");
        ListView<ReportSummary> reports=new ListView<>();ListView<VersionSummary> versions=new ListView<>();Label info=new Label();
        reports.setCellFactory(v->new ListCell<>(){protected void updateItem(ReportSummary s,boolean empty){super.updateItem(s,empty);setText(empty||s==null?null:s.getClient()+" · "+s.getRemision()+"\n"+s.getDate()+" · "+s.getVersionCount()+" versión(es)"+(s.isPending()?" · Pendiente":""));}});
        versions.setCellFactory(v->new ListCell<>(){protected void updateItem(VersionSummary s,boolean empty){super.updateItem(s,empty);setText(empty||s==null?null:(s.isPending()?"Pendiente":"Versión "+s.getVersion())+" · "+s.getAuthor()+"\n"+Instant.ofEpochMilli(s.getSavedAt()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));}});
        final int[] searchRevision={0},versionRevision={0};
        Runnable search=()->{int r=++searchRevision[0];String text=query.getText();versionRevision[0]++;versions.getItems().clear();info.setText("Buscando...");background(()->services.listReports(text,DocumentKind.QUOTATION),list->{if(r==searchRevision[0]){reports.getItems().setAll(list);info.setText(list.size()+" cotización(es)");}},ex->info.setText(ex.getMessage()));};
        reports.getSelectionModel().selectedItemProperty().addListener((o,a,s)->{
            int revision=++versionRevision[0];versions.getItems().clear();if(s==null)return;
            background(()->services.listVersions(s.getReportId()),list->{if(revision==versionRevision[0])versions.getItems().setAll(list);},ex->info.setText(ex.getMessage()));
        });
        Button open=button("Abrir versión",()->{
            ReportSummary s=reports.getSelectionModel().getSelectedItem();VersionSummary v=versions.getSelectionModel().getSelectedItem();
            if(s==null||v==null)return;
            background(()->services.loadReport(s.getReportId(),v),loaded->{
                if(loaded.getQuotation()==null){info.setText("Este documento no es una cotización.");return;}
                if(!confirmClose())return;
                root.setRight(null);
                quotation=loaded.getQuotation();template=loaded.getTemplate()==null?TemplateDefinition.quotationDefaults():loaded.getTemplate();
                id=loaded.getReportId();version=loaded.getVersion();rebuildForm();savedFingerprint=fingerprint();changed();dialog.close();
            },ex->info.setText(ex.getMessage()));
        });
        Button deleteVersion=button("Eliminar versión",()->{
            ReportSummary s=reports.getSelectionModel().getSelectedItem();VersionSummary v=versions.getSelectionModel().getSelectedItem();
            if(s!=null&&v!=null&&confirm("Eliminar versión","¿Eliminar la versión seleccionada?"))background(()->{services.deleteVersion(s.getReportId(),v);return true;},ok->search.run(),ex->info.setText(ex.getMessage()));
        });
        Button delete=button("Eliminar cotización",()->{
            ReportSummary s=reports.getSelectionModel().getSelectedItem();
            if(s!=null&&confirm("Eliminar cotización","¿Eliminar la cotización y todas sus versiones?"))background(()->{services.deleteReport(s.getReportId());return true;},ok->search.run(),ex->info.setText(ex.getMessage()));
        });
        query.setOnAction(e->search.run());
        VBox content=new VBox(10,new HBox(8,query,button("Buscar",search)),info,new SplitPane(reports,versions),new HBox(8,open,deleteVersion,delete));
        dialog.getDialogPane().setContent(content);dialog.getDialogPane().setPrefSize(850,550);search.run();dialog.showAndWait();
    }
    private File choosePdf(){
        FileChooser chooser=new FileChooser();chooser.setTitle("Guardar cotización PDF");
        chooser.setInitialFileName("Cotizacion_"+quotation.value("folio").replaceAll("[^\\p{L}\\p{N}_-]","_")+".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));return chooser.showSaveDialog(root.getScene().getWindow());
    }
    private void exportPdf(){
        if(!valid())return;File target=choosePdf();if(target==null)return;ReportSnapshot s=snapshot();
        background(()->{output.quotation(target,s.getQuotation(),s.getTemplate());return target;},f->message("PDF generado",f.getAbsolutePath()),ex->message("No se pudo generar el PDF",ex.getMessage()));
    }
    private void print(){
        if(!valid())return;ReportSnapshot s=snapshot();
        background(()->{Path pdf=Files.createTempFile("teosa-cotizacion-print-",".pdf");try{output.quotation(pdf.toFile(),s.getQuotation(),s.getTemplate());return pdf;}catch(Exception ex){Files.deleteIfExists(pdf);throw ex;}},pdf->{
            try{PdfPrintSupport.print(pdf,root.getScene().getWindow(),"Cotización "+quotation.value("folio"));}catch(Exception ex){message("No se pudo imprimir",ex.getMessage());}
            finally{try{Files.deleteIfExists(pdf);}catch(IOException ignored){pdf.toFile().deleteOnExit();}}
        },ex->message("No se pudo preparar la impresión",ex.getMessage()));
    }
    private void message(String title,String text){Alert alert=new Alert(Alert.AlertType.INFORMATION,text,ButtonType.OK);alert.setHeaderText(title);App.applyTheme(alert.getDialogPane());alert.showAndWait();}
    private boolean confirm(String title,String text){Alert alert=new Alert(Alert.AlertType.CONFIRMATION,text,ButtonType.YES,ButtonType.NO);alert.setHeaderText(title);App.applyTheme(alert.getDialogPane());return alert.showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}
    @FunctionalInterface private interface Operation<T>{T run()throws Exception;}
    private <T> void background(Operation<T> op,Consumer<T> success,Consumer<Throwable> failure){
        Task<T> task=new Task<>(){protected T call()throws Exception{return op.run();}};
        task.setOnSucceeded(e->{if(!disposed)success.accept(task.getValue());});task.setOnFailed(e->{if(!disposed)failure.accept(task.getException());});Thread.ofVirtual().start(task);
    }
    private boolean numbersValid(){if(invalid.isEmpty())return true;message("Revisa los importes","Corrige los números marcados en rojo antes de continuar.");return false;}
    public void dispose(){disposed=true;debounce.stop();catalogRefresh.stop();services.removeStatusListener(statusListener);}
}
