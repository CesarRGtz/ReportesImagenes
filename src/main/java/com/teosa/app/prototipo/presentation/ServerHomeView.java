package com.teosa.app.prototipo.presentation;

import com.teosa.app.prototipo.application.AppServices;
import com.teosa.app.prototipo.domain.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Server dashboard. Editors remain alive when returning to the home screen. */
public final class ServerHomeView extends BorderPane {
    private final AppServices services;
    private final com.teosa.app.prototipo.application.port.DocumentCodec codec;
    private final Label status=text("","dashboard-status");
    private final Label reportsCount=text("Cargando documentos…","dashboard-detail");
    private final Label quotesCount=text("Cargando documentos…","dashboard-detail");
    private final Label reportsNew=text("","dashboard-detail"),quotesNew=text("","dashboard-detail");
    private final VBox activity=new VBox(12);
    private final BorderPane content=new BorderPane();
    private boolean loading;

    public ServerHomeView(AppServices services,com.teosa.app.prototipo.application.port.DocumentCodec codec,Runnable reports,Runnable quotations) {
        this.services=services;this.codec=codec;setId("server-home");getStyleClass().add("dashboard");
        getStylesheets().add(ServerHomeView.class.getResource("/com/teosa/app/prototipo/server-home.css").toExternalForm());
        Label brand=text("TEOSA","dashboard-brand");
        VBox branding=new VBox(2,brand,text("Servicios Técnicos","dashboard-subbrand"));branding.getStyleClass().add("dashboard-branding");
        Button home=AppIcons.button("Inicio"),settings=AppIcons.button("Configuración");
        home.getStyleClass().addAll("dashboard-nav","selected");settings.getStyleClass().add("dashboard-nav");
        home.setMaxWidth(Double.MAX_VALUE);settings.setMaxWidth(Double.MAX_VALUE);settings.setId("server-settings");
        home.setOnAction(e->{settings.getStyleClass().remove("selected");if(!home.getStyleClass().contains("selected"))home.getStyleClass().add("selected");setCenter(content);refresh();});settings.setOnAction(e->{home.getStyleClass().remove("selected");if(!settings.getStyleClass().contains("selected"))settings.getStyleClass().add("selected");showSettings();});
        Region spacer=new Region();VBox.setVgrow(spacer,Priority.ALWAYS);
        VBox sidebar=new VBox(5,branding,home,settings,spacer,text("v2.0 Local","dashboard-version"));sidebar.getStyleClass().add("dashboard-sidebar");setLeft(sidebar);
        VBox heading=new VBox(3,text("Panel de Control TEOSA - Sistema Local","dashboard-title"),status);heading.getStyleClass().add("dashboard-heading");content.setTop(heading);
        VBox modules=new VBox(18,card("Módulo de Reportes Fotográficos",reportsCount,reportsNew,"#087ee0"),card("Módulo de Cotizaciones",quotesCount,quotesNew,"#18b557"));
        Button reportButton=launch("Reportes","open-reports","reports-access",false,reports);
        Button quoteButton=launch("Cotizaciones","open-quotations","quotes-access",true,quotations);
        HBox actions=new HBox(15,reportButton,quoteButton);actions.getStyleClass().add("dashboard-actions");
        HBox.setHgrow(reportButton,Priority.ALWAYS);HBox.setHgrow(quoteButton,Priority.ALWAYS);modules.getChildren().add(actions);
        VBox recent=new VBox(18,text("Resumen de Actividad Reciente","dashboard-card-title"),activity);recent.getStyleClass().add("dashboard-card");recent.setId("server-activity");
        ScrollPane recentScroll=new ScrollPane(recent);recentScroll.setFitToWidth(true);recentScroll.setFitToHeight(true);recentScroll.getStyleClass().add("dashboard-scroll");
        GridPane grid=new GridPane();grid.setHgap(20);
        for(int i=0;i<2;i++){ColumnConstraints column=new ColumnConstraints();column.setPercentWidth(50);column.setMinWidth(0);grid.getColumnConstraints().add(column);}
        grid.add(modules,0,0);grid.add(recentScroll,1,0);GridPane.setVgrow(recentScroll,Priority.ALWAYS);
        content.setCenter(grid);content.getStyleClass().add("dashboard-content");setCenter(content);
    }
    private static Label text(String value,String style){Label label=new Label(value);label.getStyleClass().add(style);label.setWrapText(true);return label;}
    private VBox card(String title,Label count,Label pending,String color){
        Region accent=new Region();accent.setMinWidth(6);accent.setStyle("-fx-background-color: "+color+";");
        VBox words=new VBox(7,text(title,"dashboard-card-title"),count,pending);HBox row=new HBox(18,accent,words);HBox.setHgrow(words,Priority.ALWAYS);
        VBox box=new VBox(row);box.getStyleClass().add("dashboard-card");box.setMinHeight(130);box.setPrefHeight(140);return box;
    }
    private Button launch(String title,String id,String style,boolean quote,Runnable action){
        Button button=AppIcons.button();
        Pane icon=AppIcons.graphic(quote?"quote_detail":"report_detail",66,button.textFillProperty());
        Label caption=text(title,"dashboard-access-title");HBox graphic=new HBox(18,icon,caption);graphic.setAlignment(Pos.CENTER);
        button.setGraphic(graphic);button.setAccessibleText(title);button.setId(id);button.getStyleClass().addAll("dashboard-access",style);button.setMaxWidth(Double.MAX_VALUE);button.setMinWidth(0);button.setPrefWidth(300);button.setOnAction(e->action.run());return button;
    }
    private void showSettings(){setCenter(new ServerSettingsView(services,codec));}
    public void refresh(){
        status.setText("Estado: "+services.getStatus());if(loading)return;loading=true;
        Thread.ofVirtual().start(()->{
            try{List<ReportSummary> documents=services.listReports("");Platform.runLater(()->{loading=false;render(documents);});}
            catch(Exception ex){Platform.runLater(()->{loading=false;reportsCount.setText("Resumen no disponible");quotesCount.setText("Resumen no disponible");activity.getChildren().setAll(text("No se pudo consultar el historial. Vuelve a Inicio para reintentar.","dashboard-detail"));});}
        });
    }
    private void render(List<ReportSummary> documents){
        updateCount(documents,DocumentKind.REPORT,reportsCount,reportsNew,"reportes");updateCount(documents,DocumentKind.QUOTATION,quotesCount,quotesNew,"cotizaciones");
        activity.getChildren().clear();
        documents.stream().sorted(java.util.Comparator.comparingLong(ReportSummary::getModifiedAt).reversed()).limit(12).forEach(d->{
            String date=DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm").format(Instant.ofEpochMilli(d.getModifiedAt()).atZone(ZoneId.systemDefault()));
            boolean report=d.getKind()==DocumentKind.REPORT;
            Label heading=text((report?"Reporte":"Cotización")+" · "+(d.getClient()==null||d.getClient().isBlank()?"Sin cliente":d.getClient()),"activity-title");
            heading.getStyleClass().add(report?"activity-report-title":"activity-quote-title");
            Pane icon=AppIcons.graphic(report?"report_detail":"quote_detail",32,heading.textFillProperty());
            StackPane badge=new StackPane(icon);badge.getStyleClass().addAll("activity-icon",report?"activity-report":"activity-quote");
            badge.setMinSize(44,44);badge.setPrefSize(44,44);badge.setMaxSize(44,44);
            Label timestamp=text("Última modificación: "+date,"activity-date");
            VBox details=new VBox(4,heading,timestamp);details.setMinWidth(0);HBox.setHgrow(details,Priority.ALWAYS);
            HBox row=new HBox(12,badge,details);row.setAlignment(Pos.TOP_LEFT);row.getStyleClass().add("activity-row");
            activity.getChildren().add(row);
        });
        if(documents.isEmpty())activity.getChildren().add(text("Todavía no hay documentos guardados.","dashboard-detail"));
    }
    private void updateCount(List<ReportSummary> documents,DocumentKind kind,Label count,Label pending,String noun){
        var selected=documents.stream().filter(d->d.getKind()==kind).toList();count.setText(selected.size()+" "+noun+" en el historial");
        pending.setText(selected.stream().filter(d->d.registeredIn(YearMonth.now(),ZoneId.systemDefault())).map(ReportSummary::getReportId).distinct().count()+" "+(kind==DocumentKind.REPORT?"nuevos":"nuevas")+" este mes");
        pending.setTooltip(new Tooltip("Según su primer registro en el servidor. Las actualizaciones no vuelven a contarse."));
    }
}
