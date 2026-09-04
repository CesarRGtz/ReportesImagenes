package com.teosa.app.prototipo;

import com.teosa.app.prototipo.data.*;
import com.teosa.app.prototipo.network.AppServices;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import javafx.application.Platform;
import javafx.animation.AnimationTimer;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PrimaryController {

    private static final String DRAG_PHOTO_PREFIX = "TEOSA_PHOTO:";
    private static final String DEFAULT_TEMPLATE_NAME = "Formato predeterminado";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private TextField txtCliente;
    @FXML private DatePicker dpFecha;
    @FXML private TextField txtArea;
    @FXML private TextField txtRemision;
    @FXML private TextField txtCotizacion;
    @FXML private TextField txtFactura;
    @FXML private TextArea txtDatosEquipo;
    @FXML private TextArea txtDescripcion;
    @FXML private Button btnGenerarPDF;
    @FXML private Button btnImprimir;
    @FXML private Button btnGuardarServidor;
    @FXML private Button btnImportar;
    @FXML private Button btnConfigurarEquipo;
    @FXML private ScrollPane formScrollPane;
    @FXML private VBox photoControls;
    @FXML private VBox panePreview;
    @FXML private VBox customizationContent;
    @FXML private VBox customizationDrawer;
    @FXML private ScrollPane customizationScrollPane;
    @FXML private VBox customFieldsContainer;
    @FXML private VBox serviceFieldsContainer;
    @FXML private ComboBox<TemplateDefinition> cmbPlantillas;
    @FXML private Label lblConnectionStatus;
    @FXML private Label lblFecha;
    @FXML private Label lblArea;
    @FXML private Label lblRemision;
    @FXML private Label lblCotizacion;
    @FXML private Label lblFactura;

    private final List<CategoriaFotografica> categorias = new ArrayList<>();
    private final Map<String, TextField> customValueControls = new LinkedHashMap<>();
    private final Set<String> expandedCustomizationSections = new HashSet<>();
    private TemplateDefinition activeTemplate = TemplateDefinition.defaults();
    private String currentReportId;
    private int currentVersion;
    private String lastSavedFingerprint;
    private boolean applyingData;
    private boolean actualizacionPreviewPendiente;
    private final AnimationTimer actualizadorPreview = new AnimationTimer() {
        private long ultimaActualizacion;

        @Override
        public void handle(long ahora) {
            if (actualizacionPreviewPendiente
                    && ahora - ultimaActualizacion >= 33_000_000L) {
                actualizacionPreviewPendiente = false;
                ultimaActualizacion = ahora;
                actualizarPreview();
            }
        }
    };

    @FXML
    private void initialize() {
        dpFecha.setValue(LocalDate.now());
        actualizadorPreview.start();

        txtCliente.textProperty().addListener((o, a, b) -> onDataChanged());
        dpFecha.valueProperty().addListener((o, a, b) -> onDataChanged());
        txtArea.textProperty().addListener((o, a, b) -> onDataChanged());
        txtRemision.textProperty().addListener((o, a, b) -> onDataChanged());
        txtCotizacion.textProperty().addListener((o, a, b) -> onDataChanged());
        txtFactura.textProperty().addListener((o, a, b) -> onDataChanged());
        txtDatosEquipo.textProperty().addListener((o, a, b) -> onDataChanged());
        txtDescripcion.textProperty().addListener((o, a, b) -> onDataChanged());

        cmbPlantillas.setConverter(new StringConverter<>() {
            @Override public String toString(TemplateDefinition template) {
                return template == null ? "" : template.getName();
            }
            @Override public TemplateDefinition fromString(String value) { return null; }
        });
        cmbPlantillas.setOnAction(event -> {
            TemplateDefinition selected = cmbPlantillas.getValue();
            if (selected != null && selected != activeTemplate) aplicarPlantilla(selected);
        });
        AppServices.get().addStatusListener(status -> Platform.runLater(() -> {
            lblConnectionStatus.setText(status);
            lblConnectionStatus.setTextFill(status.startsWith("Sin")
                    ? Color.web("#b45309") : Color.web("#047857"));
            actualizarBotonConfiguracionEquipo();
        }));

        actualizarBotonConfiguracionEquipo();
        actualizarControlesFotos();
        construirPanelPersonalizacion();
        cargarPlantillas(true);
        actualizarPreview();
    }

    private void onDataChanged() {
        if (!applyingData) actualizarPreview();
    }

    @FXML
    private void handleConfigurarEquipo() {
        AppConfig current = AppServices.get().getConfig();
        AppConfig.Role currentRole = current == null ? null : current.getRole();
        Optional<AppConfig.Role> selected = RoleConfigurationDialog.show(
                btnConfigurarEquipo.getScene().getWindow(), currentRole, false);
        if (selected.isEmpty() || selected.get() == currentRole) return;

        AppConfig previous = copyConfig(current);
        AppConfig updated = copyConfig(current);
        updated.setRole(selected.get());
        btnConfigurarEquipo.setDisable(true);
        btnConfigurarEquipo.setText("Aplicando configuración...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    AppServices.get().initialize(updated);
                    ConfigStore.save(updated);
                } catch (Exception changeError) {
                    try {
                        AppServices.get().initialize(previous);
                        ConfigStore.save(previous);
                    } catch (Exception rollbackError) {
                        changeError.addSuppressed(rollbackError);
                    }
                    throw changeError;
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            btnConfigurarEquipo.setDisable(false);
            actualizarBotonConfiguracionEquipo();
            cargarPlantillas();
            String mode = selected.get() == AppConfig.Role.PRIMARY
                    ? "servidor principal" : "computadora secundaria";
            mostrarAlerta(Alert.AlertType.INFORMATION, "Configuración actualizada",
                    "Este equipo ahora funciona como " + mode + ". El reporte abierto se conservó.");
        });
        task.setOnFailed(event -> {
            btnConfigurarEquipo.setDisable(false);
            actualizarBotonConfiguracionEquipo();
            Throwable error = task.getException();
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo cambiar la configuración",
                    error == null ? "Ocurrió un error inesperado." : error.getMessage());
        });
        Thread.ofVirtual().name("teosa-role-change").start(task);
    }

    private AppConfig copyConfig(AppConfig source) {
        AppConfig copy = new AppConfig();
        if (source != null) {
            copy.setRole(source.getRole());
            copy.setServerUrl(source.getServerUrl());
            copy.setServerPort(source.getServerPort());
        }
        return copy;
    }

    private void actualizarBotonConfiguracionEquipo() {
        if (btnConfigurarEquipo == null) return;
        AppConfig config = AppServices.get().getConfig();
        String mode = config != null && config.getRole() == AppConfig.Role.PRIMARY
                ? "Servidor principal" : "Equipo secundario";
        btnConfigurarEquipo.setText("⚙  " + mode);
    }

    @FXML
    private void handleTogglePersonalizacion() {
        boolean show = !customizationDrawer.isVisible();
        customizationDrawer.setVisible(show);
        customizationDrawer.setManaged(show);
    }

    @FXML
    private void handleAgregarCategoria() {
        categorias.add(new CategoriaFotografica("Nueva actividad o etapa"));
        actualizarControlesFotos();
        actualizarPreview();
    }

    @FXML
    private void handleAgregarFoto() {
        if (categorias.isEmpty()) {
            categorias.add(new CategoriaFotografica("Evidencia fotográfica"));
        }
        seleccionarFotosParaCategoria(categorias.get(categorias.size() - 1));
    }

    private void seleccionarFotosParaCategoria(CategoriaFotografica categoria) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar fotografías");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg")
        );
        List<File> seleccionadas = fileChooser.showOpenMultipleDialog(btnGenerarPDF.getScene().getWindow());
        if (seleccionadas != null && !seleccionadas.isEmpty()) {
            agregarArchivos(categoria, categoria.getFotografias().size(), seleccionadas);
            actualizarControlesFotos();
            actualizarPreview();
        }
    }

    private ReporteServicio crearReporteActual() {
        String fecha = dpFecha.getValue() == null ? "" : dpFecha.getValue().format(FORMATO_FECHA);
        ReporteServicio reporte = new ReporteServicio(
                txtCliente.getText(), fecha, txtArea.getText(), txtRemision.getText(),
                txtCotizacion.getText(), txtFactura.getText(), txtDatosEquipo.getText(),
                txtDescripcion.getText());
        for (FieldDefinition definition : activeTemplate.orderedFields()) {
            if (definition.isCustom()) {
                TextField control = customValueControls.get(definition.getKey());
                reporte.getCustomFields().add(new CustomFieldValue(
                        definition.getKey(), control == null ? "" : control.getText()));
            }
        }
        for (CategoriaFotografica categoria : categorias) {
            CategoriaFotografica copy = new CategoriaFotografica(categoria.getTitulo());
            copy.setSaltoPaginaDespues(categoria.isSaltoPaginaDespues());
            for (FotoEvidencia foto : categoria.getFotografias()) {
                FotoEvidencia photoCopy = new FotoEvidencia(foto.getRuta(), foto.getEtiqueta());
                photoCopy.setAncho(foto.getAncho());
                photoCopy.setRutaOriginal(foto.getRutaOriginal());
                photoCopy.setCropX(foto.getCropX());
                photoCopy.setCropY(foto.getCropY());
                photoCopy.setCropWidth(foto.getCropWidth());
                photoCopy.setCropHeight(foto.getCropHeight());
                copy.agregarFotografia(photoCopy);
            }
            reporte.agregarCategoriaFotografica(copy);
        }
        return reporte;
    }

    private ReportSnapshot crearSnapshotActual() {
        ReportSnapshot snapshot = new ReportSnapshot();
        if (currentReportId == null || currentReportId.isBlank()) currentReportId = UUID.randomUUID().toString();
        snapshot.setReportId(currentReportId);
        snapshot.setVersion(currentVersion);
        snapshot.setReport(crearReporteActual());
        snapshot.setTemplate(JsonSupport.GSON.fromJson(
                JsonSupport.GSON.toJson(activeTemplate), TemplateDefinition.class));
        return snapshot;
    }

    private String fingerprintActual() {
        return JsonSupport.GSON.toJson(crearReporteActual()) + "\n"
                + JsonSupport.GSON.toJson(activeTemplate);
    }

    private String fingerprint(ReportSnapshot snapshot) {
        return JsonSupport.GSON.toJson(snapshot.getReport()) + "\n"
                + JsonSupport.GSON.toJson(snapshot.getTemplate());
    }

    private boolean tieneCambios() {
        return lastSavedFingerprint == null || !lastSavedFingerprint.equals(fingerprintActual());
    }

    @FXML
    private void handleGuardarServidor() {
        if (txtCliente.getText() == null || txtCliente.getText().isBlank()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Falta información",
                    "Captura el cliente antes de guardar el reporte.");
            return;
        }
        btnGuardarServidor.setDisable(true);
        btnGuardarServidor.setText("Guardando...");
        ReportSnapshot snapshot = crearSnapshotActual();
        String savedFingerprint = fingerprint(snapshot);
        Task<SaveResponse> task = new Task<>() {
            @Override protected SaveResponse call() throws Exception {
                return AppServices.get().saveReport(snapshot);
            }
        };
        task.setOnSucceeded(event -> {
            SaveResponse response = task.getValue();
            currentReportId = response.getReportId() == null
                    ? snapshot.getReportId() : response.getReportId();
            if (response.getVersion() > 0) currentVersion = response.getVersion();
            lastSavedFingerprint = savedFingerprint;
            btnGuardarServidor.setDisable(false);
            btnGuardarServidor.setText("Guardar en servidor");
            mostrarAlerta(Alert.AlertType.INFORMATION,
                    response.isQueued() ? "Guardado pendiente" : "Reporte guardado",
                    response.getMessage());
        });
        task.setOnFailed(event -> {
            btnGuardarServidor.setDisable(false);
            btnGuardarServidor.setText("Guardar en servidor");
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo guardar",
                    task.getException().getMessage());
        });
        Thread.ofVirtual().start(task);
    }

    private boolean guardarAntesDePdfSiCorresponde() {
        if (!tieneCambios()) return true;
        Alert question = new Alert(Alert.AlertType.CONFIRMATION);
        aplicarTema(question);
        question.setTitle("Cambios sin guardar");
        question.setHeaderText("Este reporte tiene cambios sin guardar");
        question.setContentText("¿Deseas guardar una nueva versión antes de generar el PDF?");
        ButtonType save = new ButtonType("Guardar versión");
        ButtonType continueWithoutSaving = new ButtonType("Generar sin guardar");
        question.getButtonTypes().setAll(save, continueWithoutSaving, ButtonType.CANCEL);
        Optional<ButtonType> result = question.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) return false;
        if (result.get() == save) {
            try {
                ReportSnapshot snapshot = crearSnapshotActual();
                SaveResponse response = AppServices.get().saveReport(snapshot);
                currentReportId = response.getReportId() == null ? snapshot.getReportId() : response.getReportId();
                if (response.getVersion() > 0) currentVersion = response.getVersion();
                lastSavedFingerprint = fingerprintActual();
            } catch (Exception ex) {
                mostrarAlerta(Alert.AlertType.ERROR, "No se pudo guardar", ex.getMessage());
                return false;
            }
        }
        return true;
    }

    @FXML
    private void handleImportarReporte() {
        Dialog<ButtonType> dialog = new Dialog<>();
        aplicarTema(dialog);
        dialog.setTitle("Importar reporte e historial");
        dialog.setHeaderText("Selecciona un reporte y una versión");
        dialog.initOwner(btnImportar.getScene().getWindow());
        ButtonType open = new ButtonType("Abrir versión", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(open, ButtonType.CANCEL);

        TextField search = new TextField();
        search.setPromptText("Buscar por cliente, fecha, área, remisión o autor");
        Button searchButton = new Button("Buscar");
        searchButton.getStyleClass().add("button-primary");
        HBox searchRow = new HBox(8, search, searchButton);
        HBox.setHgrow(search, Priority.ALWAYS);

        ListView<ReportSummary> reports = new ListView<>();
        ListView<VersionSummary> versions = new ListView<>();
        reports.setPrefWidth(460);
        versions.setPrefWidth(280);
        reports.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(ReportSummary item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getClient() + " · " + item.getDate()
                        + "\n" + valorSinNulo(item.getArea()) + " · " + item.getVersionCount()
                        + " versión(es) · " + valorSinNulo(item.getLastAuthor()));
            }
        });
        versions.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(VersionSummary item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "Versión " + item.getVersion() + " · "
                        + formatTimestamp(item.getSavedAt()) + "\n"
                        + valorSinNulo(item.getAuthor()) + " @ " + valorSinNulo(item.getComputer()));
            }
        });

        Button deleteVersion = new Button("Eliminar versión");
        Button deleteReport = new Button("Eliminar reporte completo");
        deleteVersion.getStyleClass().add("button-danger");
        deleteReport.getStyleClass().add("button-danger");
        VBox right = new VBox(8, new Label("Versiones"), versions,
                new HBox(8, deleteVersion, deleteReport));
        VBox.setVgrow(versions, Priority.ALWAYS);
        HBox lists = new HBox(12, reports, right);
        VBox content = new VBox(10, searchRow, new Label("Reportes guardados"), lists);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(820, 560);

        Runnable loadReports = () -> {
            try {
                reports.setItems(FXCollections.observableArrayList(
                        AppServices.get().listReports(search.getText())));
                versions.getItems().clear();
            } catch (Exception ex) {
                mostrarAlerta(Alert.AlertType.ERROR, "Servidor no disponible", ex.getMessage());
            }
        };
        searchButton.setOnAction(event -> loadReports.run());
        search.setOnAction(event -> loadReports.run());
        reports.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == null) return;
            try {
                versions.setItems(FXCollections.observableArrayList(
                        AppServices.get().listVersions(selected.getReportId())));
                if (!versions.getItems().isEmpty()) versions.getSelectionModel().selectFirst();
            } catch (Exception ex) {
                mostrarAlerta(Alert.AlertType.ERROR, "No se pudo cargar el historial", ex.getMessage());
            }
        });
        deleteVersion.setOnAction(event -> {
            ReportSummary report = reports.getSelectionModel().getSelectedItem();
            VersionSummary version = versions.getSelectionModel().getSelectedItem();
            if (report == null || version == null || !confirmar("Eliminar versión",
                    "¿Eliminar definitivamente la versión " + version.getVersion() + "?")) return;
            try {
                AppServices.get().deleteVersion(report.getReportId(), version.getVersion());
                loadReports.run();
            } catch (Exception ex) { mostrarAlerta(Alert.AlertType.ERROR, "No se pudo eliminar", ex.getMessage()); }
        });
        deleteReport.setOnAction(event -> {
            ReportSummary report = reports.getSelectionModel().getSelectedItem();
            if (report == null || !confirmar("Eliminar reporte",
                    "¿Eliminar el reporte y todas sus versiones?")) return;
            try {
                AppServices.get().deleteReport(report.getReportId());
                loadReports.run();
            } catch (Exception ex) { mostrarAlerta(Alert.AlertType.ERROR, "No se pudo eliminar", ex.getMessage()); }
        });

        loadReports.run();
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != open) return;
        ReportSummary report = reports.getSelectionModel().getSelectedItem();
        VersionSummary version = versions.getSelectionModel().getSelectedItem();
        if (report == null || version == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Selecciona una versión",
                    "Selecciona primero un reporte y una versión de su historial.");
            return;
        }
        try {
            aplicarSnapshot(AppServices.get().loadReport(report.getReportId(), version.getVersion()));
        } catch (Exception ex) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo importar", ex.getMessage());
        }
    }

    @FXML
    private void handleNuevoReporte() {
        if (formularioTieneContenido() && tieneCambios()
                && !confirmar("Nuevo reporte", "Hay cambios sin guardar. ¿Deseas descartarlos?")) return;
        applyingData = true;
        currentReportId = null;
        currentVersion = 0;
        lastSavedFingerprint = null;
        txtCliente.clear();
        dpFecha.setValue(LocalDate.now());
        txtArea.clear(); txtRemision.clear(); txtCotizacion.clear(); txtFactura.clear();
        txtDatosEquipo.clear(); txtDescripcion.clear();
        categorias.clear();
        for (TextField field : customValueControls.values()) field.clear();
        aplicarValoresPreestablecidos(activeTemplate);
        applyingData = false;
        actualizarControlesFotos();
        actualizarPreview();
    }

    private void aplicarSnapshot(ReportSnapshot snapshot) {
        applyingData = true;
        currentReportId = snapshot.getReportId();
        currentVersion = snapshot.getVersion();
        ReporteServicio report = snapshot.getReport();
        activeTemplate = snapshot.getTemplate() == null ? TemplateDefinition.defaults() : snapshot.getTemplate();
        txtCliente.setText(valorSinNulo(report.getCliente()));
        try { dpFecha.setValue(LocalDate.parse(report.getFecha(), FORMATO_FECHA)); }
        catch (Exception ex) { dpFecha.setValue(LocalDate.now()); }
        txtArea.setText(valorSinNulo(report.getArea()));
        txtRemision.setText(valorSinNulo(report.getRemision()));
        txtCotizacion.setText(valorSinNulo(report.getCotizacion()));
        txtFactura.setText(valorSinNulo(report.getFactura()));
        txtDatosEquipo.setText(valorSinNulo(report.getDatosEquipo()));
        txtDescripcion.setText(valorSinNulo(report.getDescripcion()));
        categorias.clear();
        categorias.addAll(report.getCategoriasFotograficas());
        actualizarEtiquetasYCampos();
        for (CustomFieldValue value : report.getCustomFields()) {
            TextField field = customValueControls.get(value.getKey());
            if (field != null) field.setText(value.getValue());
        }
        construirPanelPersonalizacion();
        applyingData = false;
        cargarPlantillas();
        actualizarControlesFotos();
        actualizarPreview();
        lastSavedFingerprint = fingerprintActual();
    }

    private boolean formularioTieneContenido() {
        return !valorSinNulo(txtCliente.getText()).isBlank()
                || !valorSinNulo(txtDatosEquipo.getText()).isBlank()
                || !valorSinNulo(txtDescripcion.getText()).isBlank() || !categorias.isEmpty();
    }

    private boolean confirmar(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        aplicarTema(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private String formatTimestamp(long value) {
        if (value <= 0) return "Sin fecha";
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value));
    }

    private void cargarPlantillas() {
        cargarPlantillas(false);
    }

    private void cargarPlantillas(boolean applyAtStartup) {
        List<TemplateDefinition> templates = new ArrayList<>();
        try { templates.addAll(AppServices.get().listTemplates()); }
        catch (Exception ignored) {}
        templates.removeIf(template -> template == null || template.getName().isBlank());

        TemplateDefinition defaultTemplate = templates.stream()
                .filter(template -> isDefaultTemplateName(template.getName()))
                .findFirst().orElseGet(TemplateDefinition::defaults);
        templates.remove(defaultTemplate);
        templates.add(0, defaultTemplate);

        boolean hasActive = templates.stream().anyMatch(template ->
                template.getName().equalsIgnoreCase(activeTemplate.getName()));
        if (!hasActive && !activeTemplate.getName().isBlank()) {
            templates.add(copyTemplate(activeTemplate));
        }
        applyingData = true;
        cmbPlantillas.setItems(FXCollections.observableArrayList(templates));
        TemplateDefinition selected = templates.stream()
                .filter(t -> t.getName().equalsIgnoreCase(activeTemplate.getName()))
                .findFirst()
                .orElseGet(() -> templates.stream()
                        .filter(t -> isDefaultTemplateName(t.getName()))
                        .findFirst().orElseGet(TemplateDefinition::defaults));
        if (applyAtStartup) {
            activeTemplate = copyTemplate(selected);
            actualizarEtiquetasYCampos();
            aplicarValoresPreestablecidos(activeTemplate);
            construirPanelPersonalizacion();
        }
        cmbPlantillas.getSelectionModel().select(selected);
        applyingData = false;
        if (applyAtStartup) actualizarPreview();
    }

    private void aplicarPlantilla(TemplateDefinition selected) {
        if (applyingData) return;
        activeTemplate = copyTemplate(selected);
        applyingData = true;
        actualizarEtiquetasYCampos();
        aplicarValoresPreestablecidos(activeTemplate);
        activeTemplate.setLastUsedAt(System.currentTimeMillis());
        construirPanelPersonalizacion();
        applyingData = false;
        actualizarPreview();
        TemplateDefinition usedTemplate = JsonSupport.GSON.fromJson(
                JsonSupport.GSON.toJson(activeTemplate), TemplateDefinition.class);
        Thread.ofVirtual().start(() -> {
            try { AppServices.get().saveTemplate(usedTemplate); }
            catch (Exception ignored) {}
        });
    }

    private void construirPanelPersonalizacion() {
        if (customizationContent == null) return;
        double scroll = customizationScrollPane == null ? 0 : customizationScrollPane.getVvalue();
        customizationContent.getChildren().clear();

        Label templateTitle = new Label("Guardar configuración como plantilla");
        templateTitle.setFont(Font.font("System", FontWeight.BOLD, 11));
        templateTitle.getStyleClass().add("section-title");
        TextField templateName = new TextField(activeTemplate.getName());
        templateName.setPromptText("Nombre de la plantilla");
        Button saveTemplate = new Button("Guardar plantilla");
        saveTemplate.setMaxWidth(Double.MAX_VALUE);
        saveTemplate.getStyleClass().add("button-primary");
        saveTemplate.disableProperty().bind(templateName.textProperty().isEmpty());
        saveTemplate.setOnAction(event -> {
            TemplateDefinition templateToSave = copyTemplate(activeTemplate);
            templateToSave.setName(templateName.getText().trim());
            templateToSave.setLastUsedAt(System.currentTimeMillis());
            templateToSave.setPresetValues(currentPresetValues());
            try {
                AppServices.get().saveTemplate(templateToSave);
                activeTemplate = copyTemplate(templateToSave);
                cargarPlantillas();
                construirPanelPersonalizacion();
                mostrarAlerta(Alert.AlertType.INFORMATION, "Plantilla guardada",
                        "La plantilla '" + activeTemplate.getName() + "' quedó disponible para todos los equipos.");
            } catch (Exception ex) {
                mostrarAlerta(Alert.AlertType.ERROR, "No se pudo guardar la plantilla", ex.getMessage());
            }
        });
        Button deleteTemplate = new Button("Eliminar plantilla guardada");
        deleteTemplate.setMaxWidth(Double.MAX_VALUE);
        deleteTemplate.setDisable(isDefaultTemplateName(activeTemplate.getName()));
        deleteTemplate.getStyleClass().add("button-danger");
        deleteTemplate.setOnAction(event -> eliminarPlantillaGuardada());

        VBox fieldsEditor = new VBox(7);
        for (FieldDefinition definition : activeTemplate.orderedFields()) {
            TextField label = new TextField(definition.getLabel());
            label.setPromptText("Nombre del campo");
            label.textProperty().addListener((obs, old, value) -> {
                definition.setLabel(value);
                actualizarEtiquetasYCampos();
                actualizarPreview();
            });
            ColorPicker background = colorPicker(definition.getBackgroundColor());
            background.setOnAction(event -> {
                definition.setBackgroundColor(toHex(background.getValue()));
                actualizarEtiquetasYCampos();
                actualizarPreview();
            });
            CheckBox visible = new CheckBox("Visible");
            visible.setSelected(definition.isVisible());
            visible.selectedProperty().addListener((obs, old, value) -> {
                definition.setVisible(value);
                actualizarEtiquetasYCampos();
                actualizarPreview();
            });
            Button up = new Button("↑");
            Button down = new Button("↓");
            up.setOnAction(event -> moverCampo(definition, -1));
            down.setOnAction(event -> moverCampo(definition, 1));
            HBox actions = new HBox(5, background, visible, up, down);
            if (definition.isCustom()) {
                Button remove = new Button("Eliminar");
                remove.getStyleClass().add("button-danger");
                remove.setOnAction(event -> {
                    activeTemplate.getFields().remove(definition.getKey());
                    customValueControls.remove(definition.getKey());
                    normalizarOrden();
                    construirPanelPersonalizacion();
                    actualizarEtiquetasYCampos();
                    actualizarPreview();
                });
                actions.getChildren().add(remove);
            }
            VBox card = new VBox(5, label, actions);
            card.getStyleClass().add("editor-card");
            fieldsEditor.getChildren().add(card);
        }
        Button addField = new Button("Agregar campo personalizado");
        addField.setMaxWidth(Double.MAX_VALUE);
        addField.getStyleClass().add("button-secondary");
        addField.setOnAction(event -> {
            String key = "custom-" + UUID.randomUUID();
            activeTemplate.getFields().put(key, new FieldDefinition(
                    key, "Nuevo campo:", activeTemplate.getFields().size(), true));
            construirPanelPersonalizacion();
            actualizarEtiquetasYCampos();
            actualizarPreview();
        });
        fieldsEditor.getChildren().add(addField);

        VBox sectionsEditor = new VBox(7);
        TextField section1 = new TextField(activeTemplate.getSection1Title());
        TextField section2 = new TextField(activeTemplate.getSection2Title());
        TextField section3 = new TextField(activeTemplate.getSection3Title());
        CheckBox startPhotosOnNewPage = new CheckBox("Iniciar el punto 3 en una página nueva");
        startPhotosOnNewPage.setSelected(activeTemplate.isStartPhotosOnNewPage());
        section1.setPromptText("Título del punto 1");
        section2.setPromptText("Título del punto 2");
        section3.setPromptText("Título del punto 3");
        ColorPicker sectionColor = colorPicker(activeTemplate.getSectionBackgroundColor());
        section1.textProperty().addListener((o,a,b) -> { activeTemplate.setSection1Title(b); actualizarPreview(); });
        section2.textProperty().addListener((o,a,b) -> { activeTemplate.setSection2Title(b); actualizarPreview(); });
        section3.textProperty().addListener((o,a,b) -> { activeTemplate.setSection3Title(b); actualizarPreview(); });
        startPhotosOnNewPage.selectedProperty().addListener((o,a,b) -> {
            activeTemplate.setStartPhotosOnNewPage(b);
            actualizarPreview();
        });
        sectionColor.setOnAction(event -> { activeTemplate.setSectionBackgroundColor(toHex(sectionColor.getValue())); actualizarPreview(); });
        sectionsEditor.getChildren().addAll(section1, section2, section3,
                startPhotosOnNewPage,
                new HBox(8, new Label("Color de encabezados:"), sectionColor));

        VBox headerEditor = new VBox(8);
        Label logoName = new Label("Imagen: " + activeTemplate.getHeaderImageFileName());
        logoName.setWrapText(true);
        Button chooseLogo = new Button("Elegir otra imagen");
        chooseLogo.getStyleClass().add("button-secondary");
        chooseLogo.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Seleccionar imagen del encabezado");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            File selected = chooser.showOpenDialog(customizationDrawer.getScene().getWindow());
            if (selected == null) return;
            try {
                byte[] imageBytes = Files.readAllBytes(selected.toPath());
                if (imageBytes.length > 10 * 1024 * 1024) {
                    throw new IllegalArgumentException("La imagen debe pesar menos de 10 MB");
                }
                Image selectedImage = new Image(new ByteArrayInputStream(imageBytes));
                if (selectedImage.isError() || selectedImage.getHeight() <= 0) {
                    throw new IllegalArgumentException("La imagen seleccionada no es válida");
                }
                activeTemplate.setHeaderImageBase64(Base64.getEncoder().encodeToString(imageBytes));
                activeTemplate.setHeaderImageFileName(selected.getName());
                activeTemplate.setHeaderImageAspectRatio(
                        selectedImage.getWidth() / selectedImage.getHeight());
                logoName.setText("Imagen: " + selected.getName());
                actualizarPreview();
            } catch (Exception ex) {
                mostrarAlerta(Alert.AlertType.ERROR, "No se pudo cargar la imagen", ex.getMessage());
            }
        });
        Button resetLogo = new Button("Usar logo TEOSA");
        resetLogo.setOnAction(event -> {
            activeTemplate.setHeaderImageBase64("");
            activeTemplate.setHeaderImageFileName("Imagen12.jpg");
            activeTemplate.setHeaderImageAspectRatio(135.0 / 87.0);
            logoName.setText("Imagen: Imagen12.jpg");
            actualizarPreview();
        });
        Spinner<Double> logoWidth = new Spinner<>(50.0, 260.0,
                activeTemplate.getHeaderImageWidth(), 5.0);
        Spinner<Double> headerGap = new Spinner<>(0.0, 100.0,
                activeTemplate.getHeaderGap(), 2.0);
        logoWidth.valueProperty().addListener((o,a,b) -> {
            activeTemplate.setHeaderImageWidth(b); actualizarPreview();
        });
        headerGap.valueProperty().addListener((o,a,b) -> {
            activeTemplate.setHeaderGap(b); actualizarPreview();
        });
        ComboBox<String> headerLayout = new ComboBox<>(FXCollections.observableArrayList(
                "SIDE_BY_SIDE", "STACKED"));
        headerLayout.setValue(activeTemplate.getHeaderLayout());
        headerLayout.setConverter(new StringConverter<>() {
            @Override public String toString(String value) {
                return "STACKED".equals(value) ? "Logo arriba, centrado" : "Logo y texto juntos, centrados";
            }
            @Override public String fromString(String value) { return value; }
        });
        headerLayout.setMaxWidth(Double.MAX_VALUE);
        headerLayout.setOnAction(event -> {
            activeTemplate.setHeaderLayout(headerLayout.getValue()); actualizarPreview();
        });
        ComboBox<String> headerAlignment = alignmentCombo(activeTemplate.getHeaderTextAlignment());
        headerAlignment.setOnAction(event -> {
            activeTemplate.setHeaderTextAlignment(headerAlignment.getValue()); actualizarPreview();
        });
        headerEditor.getChildren().addAll(logoName, new HBox(8, chooseLogo, resetLogo),
                new Label("Tamaño del logo:"), logoWidth,
                new Label("Espacio entre imagen y texto:"), headerGap,
                new Label("Distribución:"), headerLayout,
                new Label("Alineación del texto:"), headerAlignment);
        for (HeaderLine line : activeTemplate.getHeaderLines()) headerEditor.getChildren().add(headerLineEditor(line));
        Button addHeaderLine = new Button("Agregar línea al encabezado");
        addHeaderLine.getStyleClass().add("button-secondary");
        addHeaderLine.setOnAction(event -> {
            activeTemplate.getHeaderLines().add(new HeaderLine("Nueva línea", 12, false, false, "#5b7699"));
            construirPanelPersonalizacion();
            actualizarPreview();
        });
        headerEditor.getChildren().add(addHeaderLine);

        VBox commentEditor = textStyleEditor(activeTemplate.getPhotoCommentStyle(), this::actualizarPreview);

        VBox categoryEditor = textStyleEditor(activeTemplate.getCategoryTitleStyle(), this::actualizarPreview);
        ComboBox<String> categoryAlignment = alignmentCombo(
                activeTemplate.getCategoryTitleAlignment());
        categoryAlignment.setOnAction(event -> {
            activeTemplate.setCategoryTitleAlignment(categoryAlignment.getValue());
            actualizarPreview();
        });
        categoryEditor.getChildren().addAll(new Label("Alineación:"), categoryAlignment);

        customizationContent.getChildren().addAll(templateTitle, templateName, saveTemplate,
                deleteTemplate,
                collapsed("Encabezado", headerEditor),
                collapsed("Campos del formulario", fieldsEditor),
                collapsed("Títulos y colores de secciones", sectionsEditor),
                collapsed("Títulos de categorías", categoryEditor),
                collapsed("Comentarios de imágenes", commentEditor));
        actualizarEtiquetasYCampos();
        if (customizationScrollPane != null) {
            Platform.runLater(() -> customizationScrollPane.setVvalue(scroll));
        }
    }

    private void eliminarPlantillaGuardada() {
        String templateName = activeTemplate.getName();
        if (isDefaultTemplateName(templateName)) {
            mostrarAlerta(Alert.AlertType.WARNING, "Plantilla protegida",
                    "La plantilla predeterminada no se puede eliminar.");
            return;
        }
        Alert first = new Alert(Alert.AlertType.CONFIRMATION);
        aplicarTema(first);
        first.setTitle("Eliminar plantilla");
        first.setHeaderText("¿Deseas eliminar la plantilla '" + templateName + "'?");
        first.setContentText("La plantilla dejará de aparecer en la lista de todas las computadoras.");
        first.getButtonTypes().setAll(ButtonType.YES, ButtonType.CANCEL);
        if (first.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.YES) return;

        Alert second = new Alert(Alert.AlertType.WARNING);
        aplicarTema(second);
        second.setTitle("Confirmación final");
        second.setHeaderText("La plantilla guardada se eliminará definitivamente");
        second.setContentText("Los reportes y versiones ya guardados no se eliminarán. "
                + "¿Confirmas que deseas borrar esta plantilla?");
        ButtonType confirmDelete = new ButtonType("Sí, eliminar", ButtonBar.ButtonData.OK_DONE);
        second.getButtonTypes().setAll(confirmDelete, ButtonType.CANCEL);
        if (second.showAndWait().orElse(ButtonType.CANCEL) != confirmDelete) return;

        try {
            AppServices.get().deleteTemplate(templateName);
            activeTemplate = TemplateDefinition.defaults();
            customValueControls.clear();
            construirPanelPersonalizacion();
            cargarPlantillas();
            actualizarEtiquetasYCampos();
            actualizarPreview();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Plantilla eliminada",
                    "La plantilla '" + templateName + "' fue eliminada. El reporte actual conserva "
                            + "sus datos principales y fotografías con el formato predeterminado.");
        } catch (Exception ex) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo eliminar la plantilla", ex.getMessage());
        }
    }

    private TitledPane collapsed(String title, Node content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(expandedCustomizationSections.contains(title));
        pane.expandedProperty().addListener((obs, old, expanded) -> {
            if (expanded) expandedCustomizationSections.add(title);
            else expandedCustomizationSections.remove(title);
        });
        pane.setAnimated(false);
        return pane;
    }

    private VBox headerLineEditor(HeaderLine line) {
        TextField text = new TextField(line.getText());
        text.setPromptText("Texto; usa {empresa} para insertar el cliente");
        text.textProperty().addListener((o,a,b) -> { line.setText(b); actualizarPreview(); });
        VBox styles = textStyleEditor(line.getStyle(), this::actualizarPreview);
        Button remove = new Button("Eliminar línea");
        remove.getStyleClass().add("button-danger");
        remove.setOnAction(event -> {
            activeTemplate.getHeaderLines().remove(line);
            construirPanelPersonalizacion();
            actualizarPreview();
        });
        VBox result = new VBox(5, text, styles, remove);
        result.getStyleClass().add("editor-card");
        return result;
    }

    private VBox textStyleEditor(TextStyle style, Runnable changed) {
        ComboBox<String> font = new ComboBox<>(FXCollections.observableArrayList(
                "Arial", "Calibri", "Segoe UI", "Times New Roman", "Verdana", "Tahoma", "Courier New"));
        font.setValue(style.getFontFamily());
        font.setPrefWidth(150);
        Spinner<Double> size = new Spinner<>(7.0, 36.0, style.getFontSize(), 1.0);
        size.setPrefWidth(85);
        ColorPicker color = colorPicker(style.getColor());
        CheckBox bold = new CheckBox("Negrita"); bold.setSelected(style.isBold());
        CheckBox italic = new CheckBox("Cursiva"); italic.setSelected(style.isItalic());
        font.setOnAction(e -> { style.setFontFamily(font.getValue()); changed.run(); });
        size.valueProperty().addListener((o,a,b) -> { style.setFontSize(b); changed.run(); });
        color.setOnAction(e -> { style.setColor(toHex(color.getValue())); changed.run(); });
        bold.selectedProperty().addListener((o,a,b) -> { style.setBold(b); changed.run(); });
        italic.selectedProperty().addListener((o,a,b) -> { style.setItalic(b); changed.run(); });
        return new VBox(5, new HBox(6, font, size, color), new HBox(10, bold, italic));
    }

    private ComboBox<String> alignmentCombo(String current) {
        ComboBox<String> alignment = new ComboBox<>(FXCollections.observableArrayList(
                "LEFT", "CENTER", "JUSTIFY"));
        alignment.setValue(current == null ? "CENTER" : current);
        alignment.setConverter(new StringConverter<>() {
            @Override public String toString(String value) {
                return switch (value == null ? "CENTER" : value) {
                    case "LEFT" -> "Izquierda";
                    case "JUSTIFY" -> "Justificado";
                    default -> "Centrado";
                };
            }
            @Override public String fromString(String value) { return value; }
        });
        alignment.setMaxWidth(Double.MAX_VALUE);
        return alignment;
    }

    private void actualizarEtiquetasYCampos() {
        if (serviceFieldsContainer == null) return;
        double scroll = formScrollPane == null ? 0 : formScrollPane.getVvalue();
        Map<String, Label> labels = Map.of(
                "fecha", lblFecha, "area", lblArea, "remision", lblRemision,
                "cotizacion", lblCotizacion, "factura", lblFactura);
        Map<String, Node> controls = Map.of(
                "fecha", dpFecha, "area", txtArea, "remision", txtRemision,
                "cotizacion", txtCotizacion, "factura", txtFactura);
        serviceFieldsContainer.getChildren().clear();
        for (FieldDefinition definition : activeTemplate.orderedFields()) {
            if (!definition.isVisible()) continue;
            if (definition.isCustom()) {
                Label label = new Label(definition.getLabel());
                label.getStyleClass().add("field-label");
                TextField value = customValueControls.computeIfAbsent(definition.getKey(), key -> {
                    TextField field = new TextField();
                    field.textProperty().addListener((o,a,b) -> onDataChanged());
                    return field;
                });
                serviceFieldsContainer.getChildren().addAll(label, value);
            } else {
                Label label = labels.get(definition.getKey());
                Node control = controls.get(definition.getKey());
                if (label != null && control != null) {
                    label.setText(definition.getLabel());
                    serviceFieldsContainer.getChildren().addAll(label, control);
                }
            }
        }
        if (formScrollPane != null) {
            Platform.runLater(() -> formScrollPane.setVvalue(scroll));
        }
    }

    private Map<String, String> currentPresetValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("cliente", valorSinNulo(txtCliente.getText()));
        values.put("area", valorSinNulo(txtArea.getText()));
        values.put("remision", valorSinNulo(txtRemision.getText()));
        values.put("cotizacion", valorSinNulo(txtCotizacion.getText()));
        values.put("factura", valorSinNulo(txtFactura.getText()));
        values.put("datosEquipo", valorSinNulo(txtDatosEquipo.getText()));
        values.put("descripcion", valorSinNulo(txtDescripcion.getText()));
        customValueControls.forEach((key, control) -> values.put(key, control.getText()));
        return values;
    }

    private void aplicarValoresPreestablecidos(TemplateDefinition template) {
        for (Map.Entry<String, String> entry : template.getPresetValues().entrySet()) {
            if (!entry.getKey().equals("fecha") && !entry.getValue().isBlank()) {
                setFieldValue(entry.getKey(), entry.getValue());
            }
        }
    }

    private TemplateDefinition copyTemplate(TemplateDefinition template) {
        return JsonSupport.GSON.fromJson(
                JsonSupport.GSON.toJson(template), TemplateDefinition.class);
    }

    private boolean isDefaultTemplateName(String name) {
        return DEFAULT_TEMPLATE_NAME.equalsIgnoreCase(valorSinNulo(name).trim());
    }

    private void setFieldValue(String key, String value) {
        switch (key) {
            case "cliente" -> txtCliente.setText(value);
            case "fecha" -> { try { dpFecha.setValue(LocalDate.parse(value, FORMATO_FECHA)); } catch (Exception ignored) {} }
            case "area" -> txtArea.setText(value);
            case "remision" -> txtRemision.setText(value);
            case "cotizacion" -> txtCotizacion.setText(value);
            case "factura" -> txtFactura.setText(value);
            case "datosEquipo" -> txtDatosEquipo.setText(value);
            case "descripcion" -> txtDescripcion.setText(value);
            default -> { TextField field = customValueControls.get(key); if (field != null) field.setText(value); }
        }
    }

    private void normalizarOrden() {
        List<FieldDefinition> ordered = activeTemplate.orderedFields();
        for (int index = 0; index < ordered.size(); index++) ordered.get(index).setOrder(index);
    }

    private void moverCampo(FieldDefinition definition, int direction) {
        List<FieldDefinition> ordered = activeTemplate.orderedFields();
        int current = ordered.indexOf(definition);
        int target = Math.max(0, Math.min(ordered.size() - 1, current + direction));
        if (current == target) return;
        FieldDefinition other = ordered.get(target);
        int oldOrder = definition.getOrder();
        definition.setOrder(other.getOrder());
        other.setOrder(oldOrder);
        normalizarOrden();
        construirPanelPersonalizacion();
        actualizarEtiquetasYCampos();
        actualizarPreview();
    }

    private ColorPicker colorPicker(String color) {
        try { return new ColorPicker(Color.web(color)); }
        catch (Exception ex) { return new ColorPicker(Color.GRAY); }
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255));
    }

    @FXML
    private void handleClicAccion() {
        String cliente = txtCliente.getText();
        String fecha = dpFecha.getValue() != null ? dpFecha.getValue().format(FORMATO_FECHA) : "";

        if (cliente == null || cliente.trim().isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Falta información",
                    "Debes capturar el nombre del cliente/empresa.");
            return;
        }
        if (!guardarAntesDePdfSiCorresponde()) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar reporte PDF");
        fileChooser.setInitialFileName("Reporte_" + cliente.replaceAll("\\s+", "_") + ".pdf");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo PDF", "*.pdf"));
        File destino = fileChooser.showSaveDialog(btnGenerarPDF.getScene().getWindow());
        if (destino == null) {
            return;
        }

        ReporteServicio reporte = crearReporteActual();

        try {
            PdfReportGenerator.generar(destino, reporte, activeTemplate);
            mostrarAlerta(Alert.AlertType.INFORMATION, "PDF generado",
                    "El reporte se generó correctamente en:\n" + destino.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta(Alert.AlertType.ERROR, "Error al generar PDF",
                    "Ocurrió un error: " + e.getMessage());
        }
    }

    @FXML
    private void handleImprimir() {
        String cliente = valorSinNulo(txtCliente.getText()).trim();
        if (cliente.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Falta información",
                    "Debes capturar el nombre del cliente/empresa.");
            return;
        }
        if (!guardarAntesDePdfSiCorresponde()) return;

        Path temporaryPdf = null;
        try {
            temporaryPdf = Files.createTempFile("reporte-teosa-", ".pdf");
            temporaryPdf.toFile().deleteOnExit();
            PdfReportGenerator.generar(temporaryPdf.toFile(), crearReporteActual(), activeTemplate);
            configurarCachePdfBox();

            PrinterJob printJob = PrinterJob.createPrinterJob();
            if (printJob == null) {
                throw new IllegalStateException("Windows no reportó ninguna impresora disponible.");
            }
            printJob.getJobSettings().setJobName("Reporte TEOSA - " + cliente);
            if (!printJob.showPrintDialog(btnImprimir.getScene().getWindow())) {
                return;
            }
            if (esImpresoraPdf(printJob.getPrinter())) {
                guardarPdfDeImpresion(temporaryPdf, cliente);
                printJob.cancelJob();
                return;
            }

            try (PDDocument document = Loader.loadPDF(temporaryPdf.toFile())) {
                imprimirPaginasPdf(printJob, document);
            }
            if (!printJob.endJob()) {
                throw new IllegalStateException("La impresora no confirmó la recepción del documento.");
            }
            mostrarAlerta(Alert.AlertType.INFORMATION, "Documento enviado a impresión",
                    "El PDF del reporte se envió correctamente a la impresora seleccionada.");
        } catch (Exception ex) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo imprimir", ex.getMessage());
        } finally {
            if (temporaryPdf != null) {
                try {
                    Files.deleteIfExists(temporaryPdf);
                } catch (Exception ignored) {
                    // Windows puede mantener el archivo ocupado brevemente; deleteOnExit queda como respaldo.
                }
            }
        }
    }

    private boolean esImpresoraPdf(Printer printer) {
        return printer != null && printer.getName() != null
                && printer.getName().toLowerCase(Locale.ROOT).contains("pdf");
    }

    private void guardarPdfDeImpresion(Path sourcePdf, String cliente) throws Exception {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar impresión en PDF");
        chooser.setInitialFileName("Reporte_" + cliente.replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                + ".pdf");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivo PDF", "*.pdf"));
        File destination = chooser.showSaveDialog(btnImprimir.getScene().getWindow());
        if (destination == null) {
            return;
        }
        Files.copy(sourcePdf, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        mostrarAlerta(Alert.AlertType.INFORMATION, "Impresión guardada en PDF",
                "Se guardó exactamente el mismo documento, sin aplicar otra escala ni márgenes.");
    }

    private void configurarCachePdfBox() throws Exception {
        Path cacheDirectory = Path.of(System.getProperty("java.io.tmpdir"),
                "reportes-teosa-pdfbox-cache");
        Files.createDirectories(cacheDirectory);
        System.setProperty("pdfbox.fontcache", cacheDirectory.toString());
    }

    private void imprimirPaginasPdf(PrinterJob printJob, PDDocument document) throws Exception {
        PDFRenderer renderer = new PDFRenderer(document);
        PageLayout pageLayout = printJob.getPrinter().createPageLayout(
                Paper.NA_LETTER, PageOrientation.PORTRAIT, Printer.MarginType.HARDWARE_MINIMUM);
        printJob.getJobSettings().setPageLayout(pageLayout);
        double printableWidth = pageLayout.getPrintableWidth();
        double printableHeight = pageLayout.getPrintableHeight();
        double paperWidth = pageLayout.getPaper().getWidth();
        double paperHeight = pageLayout.getPaper().getHeight();

        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            BufferedImage renderedPage = renderer.renderImageWithDPI(pageIndex, 200, ImageType.RGB);
            ImageView pageImage = new ImageView(convertirAImagenFx(renderedPage));
            pageImage.setPreserveRatio(true);
            pageImage.setSmooth(true);
            pageImage.setFitWidth(paperWidth);
            pageImage.setFitHeight(paperHeight);
            pageImage.setManaged(false);
            pageImage.relocate(-pageLayout.getLeftMargin(), -pageLayout.getTopMargin());

            Pane printablePage = new Pane(pageImage);
            printablePage.setStyle("-fx-background-color: white;");
            printablePage.setMinSize(printableWidth, printableHeight);
            printablePage.setPrefSize(printableWidth, printableHeight);
            printablePage.setMaxSize(printableWidth, printableHeight);
            printablePage.resize(printableWidth, printableHeight);
            printablePage.setClip(new Rectangle(printableWidth, printableHeight));
            printablePage.applyCss();
            printablePage.layout();

            if (!printJob.printPage(pageLayout, printablePage)) {
                printJob.cancelJob();
                throw new IllegalStateException("No se pudo preparar la página " + (pageIndex + 1)
                        + " para impresión.");
            }
        }
    }

    private WritableImage convertirAImagenFx(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        WritableImage result = new WritableImage(width, height);
        result.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return result;
    }

    private void actualizarPreview() {
        panePreview.getChildren().clear();
        panePreview.setSpacing(25);

        VBox pagina1 = crearHojaPaginaWord();
        String empresa = valorOVacio(txtCliente.getText()).equals("—")
                ? "" : txtCliente.getText().trim().toUpperCase();
        VBox titulo = new VBox(0);
        boolean justifyHeader = "JUSTIFY".equals(activeTemplate.getHeaderTextAlignment());
        boolean leftHeader = "LEFT".equals(activeTemplate.getHeaderTextAlignment());
        titulo.setAlignment(leftHeader || justifyHeader ? Pos.CENTER_LEFT : Pos.CENTER);
        List<HeaderLine> lineasEncabezado = activeTemplate.getHeaderLines().isEmpty()
                ? TemplateDefinition.defaults().getHeaderLines() : activeTemplate.getHeaderLines();
        for (HeaderLine lineaEncabezado : lineasEncabezado) {
            Label lineaTitulo = new Label(lineaEncabezado.getText().replace("{empresa}",
                    empresa.isEmpty() ? "____________" : empresa));
            aplicarEstiloTexto(lineaTitulo, lineaEncabezado.getStyle());
            lineaTitulo.setWrapText(true);
            lineaTitulo.setMaxWidth(Double.MAX_VALUE);
            lineaTitulo.setAlignment(leftHeader || justifyHeader ? Pos.CENTER_LEFT : Pos.CENTER);
            lineaTitulo.setTextAlignment(justifyHeader
                    ? javafx.scene.text.TextAlignment.JUSTIFY
                    : leftHeader ? javafx.scene.text.TextAlignment.LEFT
                    : javafx.scene.text.TextAlignment.CENTER);
            titulo.getChildren().add(lineaTitulo);
        }

        ImageView logo = new ImageView(cargarImagenEncabezado());
        logo.setPreserveRatio(true);
        logo.setFitWidth(activeTemplate.getHeaderImageWidth());

        Node headerGroup;
        if ("STACKED".equals(activeTemplate.getHeaderLayout())) {
            titulo.setPrefWidth(ReportLayout.CONTENT_WIDTH);
            VBox stacked = new VBox(activeTemplate.getHeaderGap(), logo, titulo);
            stacked.setAlignment(Pos.CENTER);
            headerGroup = stacked;
        } else {
            double textWidth = ReportLayout.estimateHeaderTextWidth(activeTemplate, empresa);
            titulo.setPrefWidth(textWidth);
            titulo.setMinWidth(textWidth);
            titulo.setMaxWidth(textWidth);
            HBox together = new HBox(activeTemplate.getHeaderGap(), logo, titulo);
            together.setAlignment(Pos.CENTER);
            together.setMaxWidth(Region.USE_PREF_SIZE);
            headerGroup = together;
        }
        StackPane encabezadoDocumento = new StackPane(headerGroup);
        encabezadoDocumento.setPrefWidth(ReportLayout.CONTENT_WIDTH);
        encabezadoDocumento.setMaxWidth(ReportLayout.CONTENT_WIDTH);
        VBox.setMargin(encabezadoDocumento, new Insets(0, 0, 2, 0));
        pagina1.getChildren().add(encabezadoDocumento);

        javafx.scene.shape.Line linea = new javafx.scene.shape.Line(
                0, 0, ReportLayout.CONTENT_WIDTH, 0);
        linea.setStroke(Color.web("#8ca3bf"));
        linea.setStrokeWidth(0.5);
        VBox.setMargin(linea, new Insets(3, 0, 6, 0));
        pagina1.getChildren().add(linea);

        GridPane tabla = new GridPane();
        tabla.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(38);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(62);
        tabla.getColumnConstraints().addAll(col1, col2);

        int fila = 0;
        for (FieldDefinition definition : activeTemplate.orderedFields()) {
            if (definition.isVisible()) {
                agregarFilaTabla(tabla, fila++, definition.getLabel(),
                        getFieldValue(definition.getKey()), definition.getBackgroundColor());
            }
        }
        if (fila > 0) pagina1.getChildren().add(tabla);

        VBox tablaReporte = crearTablaReportePreview();
        VBox.setMargin(tablaReporte, new Insets(10, 0, 0, 0));
        agregarSeccionPreview(tablaReporte,
                activeTemplate.getSection1Title(), valorOVacio(txtDatosEquipo.getText()));
        agregarSeccionPreview(tablaReporte,
                activeTemplate.getSection2Title(), valorOVacio(txtDescripcion.getText()));
        pagina1.getChildren().add(tablaReporte);

        panePreview.getChildren().add(pagina1);
        double espacioDisponible = ReportLayout.initialPhotoSpace(
                txtDatosEquipo.getText(), txtDescripcion.getText(), activeTemplate, fila);
        agregarPaginasFotosPreview(new PreviewPageState(
                pagina1, tablaReporte, Math.max(0, espacioDisponible)));
    }

    private void actualizarControlesFotos() {
        double posicionScroll = formScrollPane == null ? 0 : formScrollPane.getVvalue();
        photoControls.getChildren().clear();

        for (int categoriaIndex = 0; categoriaIndex < categorias.size(); categoriaIndex++) {
            CategoriaFotografica categoria = categorias.get(categoriaIndex);
            VBox tarjetaCategoria = new VBox(8);
            tarjetaCategoria.getStyleClass().add("category-card");

            Label tituloControl = new Label("Categoría " + (categoriaIndex + 1));
            tituloControl.setFont(Font.font("System", FontWeight.BOLD, 11));
            tituloControl.getStyleClass().add("category-index");

            Button eliminarCategoria = new Button("Eliminar categoría");
            eliminarCategoria.getStyleClass().add("button-danger");
            eliminarCategoria.setOnAction(event -> {
                categorias.remove(categoria);
                actualizarControlesFotos();
                actualizarPreview();
            });
            Region espacioTitulo = new Region();
            HBox.setHgrow(espacioTitulo, Priority.ALWAYS);
            HBox encabezadoControl = new HBox(8, tituloControl, espacioTitulo, eliminarCategoria);
            encabezadoControl.setAlignment(Pos.CENTER_LEFT);

            TextArea tituloCategoria = new TextArea(valorSinNulo(categoria.getTitulo()));
            tituloCategoria.setPromptText("Actividad o descripción principal para varias imágenes");
            tituloCategoria.setPrefRowCount(2);
            tituloCategoria.setWrapText(true);
            tituloCategoria.textProperty().addListener((obs, oldVal, newVal) -> {
                categoria.setTitulo(newVal);
                actualizarPreview();
            });

            CheckBox saltoPagina = new CheckBox(
                    "Comenzar la siguiente categoría en una página nueva");
            saltoPagina.setSelected(categoria.isSaltoPaginaDespues());
            saltoPagina.selectedProperty().addListener((obs, oldVal, newVal) -> {
                categoria.setSaltoPaginaDespues(newVal);
                actualizarPreview();
            });

            Button agregarFotos = new Button("Agregar imágenes a esta categoría");
            agregarFotos.setMaxWidth(Double.MAX_VALUE);
            agregarFotos.getStyleClass().add("button-secondary");
            agregarFotos.setOnAction(event -> seleccionarFotosParaCategoria(categoria));
            tarjetaCategoria.getChildren().addAll(
                    encabezadoControl, tituloCategoria, saltoPagina, agregarFotos);

            for (int fotoIndex = 0; fotoIndex < categoria.getFotografias().size(); fotoIndex++) {
                FotoEvidencia foto = categoria.getFotografias().get(fotoIndex);
                Label etiquetaAncho = new Label("Imagen " + (fotoIndex + 1) + " - ancho:");
                etiquetaAncho.setFont(Font.font("System", 10));

                Slider sliderAncho = new Slider(
                        ReportLayout.MIN_PHOTO_WIDTH,
                        ReportLayout.MAX_PHOTO_WIDTH,
                        foto.getAncho());
                sliderAncho.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(sliderAncho, Priority.ALWAYS);
                sliderAncho.valueProperty().addListener((obs, oldVal, newVal) -> {
                    foto.setAncho(newVal.doubleValue());
                    actualizacionPreviewPendiente = true;
                });
                sliderAncho.setOnMouseReleased(event -> {
                    actualizacionPreviewPendiente = false;
                    actualizarPreview();
                });
                sliderAncho.setOnKeyReleased(event -> {
                    actualizacionPreviewPendiente = false;
                    actualizarPreview();
                });

                Button eliminarFoto = new Button("Eliminar");
                eliminarFoto.getStyleClass().add("button-danger");
                eliminarFoto.setOnAction(event -> {
                    categoria.getFotografias().remove(foto);
                    actualizarControlesFotos();
                    actualizarPreview();
                });

                Button recortarFoto = new Button("Editar");
                recortarFoto.setOnAction(event -> editarFotografia(foto));

                Button restaurarFoto = new Button("Restaurar original");
                restaurarFoto.setVisible(estaRecortada(foto));
                restaurarFoto.setManaged(estaRecortada(foto));
                restaurarFoto.setOnAction(event -> {
                    foto.setRuta(foto.getRutaOriginal());
                    foto.setCropX(0); foto.setCropY(0);
                    foto.setCropWidth(1); foto.setCropHeight(1);
                    actualizarControlesFotos();
                    actualizarPreview();
                });

                HBox filaAncho = new HBox(8, etiquetaAncho, sliderAncho);
                filaAncho.setAlignment(Pos.CENTER_LEFT);
                HBox accionesFoto = new HBox(8, recortarFoto, restaurarFoto, eliminarFoto);
                accionesFoto.setAlignment(Pos.CENTER_RIGHT);

                TextArea detalle = new TextArea(valorSinNulo(foto.getEtiqueta()));
                detalle.setPromptText("Descripción detallada opcional de esta imagen");
                detalle.setPrefRowCount(2);
                detalle.setWrapText(true);
                detalle.textProperty().addListener((obs, oldVal, newVal) -> {
                    foto.setEtiqueta(newVal);
                    actualizarPreview();
                });

                VBox controlesFoto = new VBox(5, filaAncho, accionesFoto, detalle);
                controlesFoto.getStyleClass().add("photo-editor-row");
                tarjetaCategoria.getChildren().add(controlesFoto);
            }

            photoControls.getChildren().add(tarjetaCategoria);
        }

        if (formScrollPane != null) {
            Platform.runLater(() -> formScrollPane.setVvalue(posicionScroll));
        }
    }

    private boolean estaRecortada(FotoEvidencia foto) {
        return foto.getRutaOriginal() != null && foto.getRuta() != null
                && !Path.of(foto.getRutaOriginal()).toAbsolutePath().normalize().equals(
                Path.of(foto.getRuta()).toAbsolutePath().normalize());
    }

    private void editarFotografia(FotoEvidencia foto) {
        String rutaActual = foto.getRuta();
        if (rutaActual == null || rutaActual.isBlank()) rutaActual = foto.getRutaOriginal();
        try {
            Optional<Path> resultado = ImageEditorDialog.mostrar(
                    btnGenerarPDF.getScene().getWindow(), Path.of(rutaActual),
                    AppDirectories.cache().resolve("ediciones"));
            if (resultado.isEmpty()) return;

            foto.setRuta(resultado.get().toAbsolutePath().toString());
            foto.setCropX(0);
            foto.setCropY(0);
            foto.setCropWidth(1);
            foto.setCropHeight(1);
            actualizarControlesFotos();
            actualizarPreview();
        } catch (Exception ex) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo editar la imagen", ex.getMessage());
        }
    }

    private void agregarPaginasFotosPreview(PreviewPageState estado) {
        if (categorias.isEmpty()) {
            return;
        }

        CategoriaFotografica primeraCategoria = categorias.get(0);
        double bloqueInicial = ReportLayout.estimatePhotoSectionHeight(
                activeTemplate.getSection3Title())
                + ReportLayout.estimateCategoryTitleHeight(
                valorOVacio(primeraCategoria.getTitulo()),
                activeTemplate.getCategoryTitleStyle().getFontSize())
                + estimarPrimeraFila(primeraCategoria);
        if (activeTemplate.isStartPhotosOnNewPage() || bloqueInicial > estado.espacioDisponible) {
            estado = crearPaginaFotos(true);
        } else {
            estado.tabla.getChildren().add(barraSeccionPreview(
                    activeTemplate.getSection3Title()));
            estado.espacioDisponible -= ReportLayout.estimatePhotoSectionHeight(
                    activeTemplate.getSection3Title());
        }

        for (int categoriaIndex = 0; categoriaIndex < categorias.size(); categoriaIndex++) {
            CategoriaFotografica categoria = categorias.get(categoriaIndex);
            boolean paginaNuevaForzada = categoriaIndex > 0
                    && categorias.get(categoriaIndex - 1).isSaltoPaginaDespues();
            if (paginaNuevaForzada) {
                estado = crearPaginaFotos(false);
            }
            String tituloCategoria = valorOVacio(categoria.getTitulo());
            double altoMinimoCategoria = ReportLayout.estimateCategoryTitleHeight(tituloCategoria,
                    activeTemplate.getCategoryTitleStyle().getFontSize())
                    + estimarPrimeraFila(categoria);
            if (!paginaNuevaForzada && altoMinimoCategoria > estado.espacioDisponible) {
                estado = crearPaginaFotos(false);
            }

            Label encabezadoCategoria = crearTituloCategoriaPreview(tituloCategoria);
            configurarDestinoDrop(
                    encabezadoCategoria, categoriaIndex, categoria.getFotografias().size());
            estado.tabla.getChildren().add(encabezadoCategoria);
            estado.espacioDisponible -= ReportLayout.estimateCategoryTitleHeight(tituloCategoria,
                    activeTemplate.getCategoryTitleStyle().getFontSize());

            if (categoria.getFotografias().isEmpty()) {
                Label zonaVacia = new Label("Arrastra imágenes aquí");
                zonaVacia.setAlignment(Pos.CENTER);
                zonaVacia.setPrefSize(ReportLayout.CONTENT_WIDTH, 80);
                zonaVacia.setMaxWidth(ReportLayout.CONTENT_WIDTH);
                zonaVacia.setStyle("-fx-border-color: black; -fx-border-width: 0 0 1 0; "
                        + "-fx-background-color: #f8fafc; -fx-text-fill: #64748b;");
                configurarDestinoDrop(zonaVacia, categoriaIndex, 0);
                estado.tabla.getChildren().add(zonaVacia);
                continue;
            }

            int inicioFila = 0;
            while (inicioFila < categoria.getFotografias().size()) {
                int finFila = calcularFinFila(categoria, inicioFila);
                double[] anchosCelda = calcularAnchosFila(categoria, inicioFila, finFila);
                List<PhotoPreviewData> datosFila = new ArrayList<>();
                double altoFilaNatural = 0;

                for (int fotoIndex = inicioFila; fotoIndex < finFila; fotoIndex++) {
                    FotoEvidencia foto = categoria.getFotografias().get(fotoIndex);
                    PhotoPreviewData datos = prepararFotoPreview(
                            foto, fotoIndex, anchosCelda[fotoIndex - inicioFila]);
                    datosFila.add(datos);
                    altoFilaNatural = Math.max(altoFilaNatural, datos.altoNatural);
                }

                if (altoFilaNatural > estado.espacioDisponible) {
                    estado = crearPaginaFotos(false);
                }

                double altoMaximoFila = Math.max(1,
                        estado.espacioDisponible - ReportLayout.PHOTO_SPACING);
                HBox fila = new HBox(ReportLayout.PHOTO_GAP);
                fila.setPrefWidth(ReportLayout.CONTENT_WIDTH);
                fila.setMaxWidth(ReportLayout.CONTENT_WIDTH);
                fila.setAlignment(Pos.TOP_LEFT);
                fila.setPadding(new Insets(ReportLayout.PHOTO_CELL_PADDING));
                if (finFila == categoria.getFotografias().size()) {
                    fila.setStyle("-fx-border-color: transparent transparent black transparent; "
                            + "-fx-border-width: 0 0 1 0;");
                }

                double altoFilaReal = 0;
                for (int columna = 0; columna < datosFila.size(); columna++) {
                    PhotoPreviewData datos = datosFila.get(columna);
                    VBox celda = crearCeldaFotoPreview(
                            datos, categoriaIndex, datos.fotoIndex, altoMaximoFila,
                            anchosCelda[columna]);
                    altoFilaReal = Math.max(altoFilaReal, datos.altoReal);
                    fila.getChildren().add(celda);
                }

                estado.tabla.getChildren().add(fila);
                estado.espacioDisponible -= altoFilaReal + ReportLayout.PHOTO_SPACING;
                inicioFila = finFila;
            }
        }
    }

    private PreviewPageState crearPaginaFotos(boolean incluirEncabezado) {
        VBox pagina = crearHojaPaginaWord();
        VBox tablaFotos = crearTablaReportePreview();
        pagina.getChildren().add(tablaFotos);
        double espacioDisponible = ReportLayout.CONTENT_HEIGHT;
        if (incluirEncabezado) {
            HBox encabezado = barraSeccionPreview(activeTemplate.getSection3Title());
            tablaFotos.getChildren().add(encabezado);
            espacioDisponible -= ReportLayout.estimatePhotoSectionHeight(
                    activeTemplate.getSection3Title());
        }
        panePreview.getChildren().add(pagina);
        return new PreviewPageState(pagina, tablaFotos, espacioDisponible);
    }

    private double estimarPrimeraFila(CategoriaFotografica categoria) {
        if (categoria.getFotografias().isEmpty()) {
            return 80;
        }
        int finFila = calcularFinFila(categoria, 0);
        double[] anchos = calcularAnchosFila(categoria, 0, finFila);
        double alto = 0;
        for (int indice = 0; indice < finFila; indice++) {
            alto = Math.max(alto, prepararFotoPreview(
                    categoria.getFotografias().get(indice), indice, anchos[indice]).altoNatural);
        }
        return alto + ReportLayout.PHOTO_SPACING;
    }

    private int calcularFinFila(CategoriaFotografica categoria, int inicio) {
        double anchoUsado = 0;
        int fin = inicio;
        while (fin < categoria.getFotografias().size()) {
            double ancho = ReportLayout.photoCellWidth(
                    categoria.getFotografias().get(fin).getAncho());
            double separacion = fin > inicio ? ReportLayout.PHOTO_GAP : 0;
            if (fin > inicio && anchoUsado + separacion + ancho > ReportLayout.MAX_PHOTO_WIDTH) {
                break;
            }
            anchoUsado += separacion + ancho;
            fin++;
        }
        return fin;
    }

    private double[] calcularAnchosFila(
            CategoriaFotografica categoria, int inicio, int fin) {
        double[] anchos = new double[fin - inicio];
        for (int indice = inicio; indice < fin; indice++) {
            anchos[indice - inicio] = ReportLayout.photoCellWidth(
                    categoria.getFotografias().get(indice).getAncho());
        }
        return anchos;
    }

    private Label crearTituloCategoriaPreview(String titulo) {
        Label encabezado = new Label(titulo);
        aplicarEstiloTexto(encabezado, activeTemplate.getCategoryTitleStyle());
        String alignment = activeTemplate.getCategoryTitleAlignment();
        encabezado.setAlignment("CENTER".equals(alignment) ? Pos.CENTER
                : Pos.CENTER_LEFT);
        encabezado.setTextAlignment("JUSTIFY".equals(alignment)
                ? javafx.scene.text.TextAlignment.JUSTIFY
                : "CENTER".equals(alignment) ? javafx.scene.text.TextAlignment.CENTER
                : javafx.scene.text.TextAlignment.LEFT);
        encabezado.setWrapText(true);
        encabezado.setPrefWidth(ReportLayout.CONTENT_WIDTH);
        encabezado.setMaxWidth(ReportLayout.CONTENT_WIDTH);
        encabezado.setPadding(new Insets(8));
        encabezado.setStyle("-fx-border-color: transparent transparent black transparent; "
                + "-fx-border-width: 0 0 1 0;");
        return encabezado;
    }

    private PhotoPreviewData prepararFotoPreview(
            FotoEvidencia foto, int fotoIndex, double anchoCelda) {
        Image imagen = new Image(new File(foto.getRuta()).toURI().toString());
        if (imagen.isError()) {
            throw new IllegalArgumentException("No se pudo cargar la imagen");
        }
        double anchoInterior = anchoCelda;
        double altoDescripcion = ReportLayout.estimateDescriptionHeight(
                foto.getEtiqueta(), anchoInterior,
                activeTemplate.getPhotoCommentStyle().getFontSize());
        double[] tamanoNatural = ReportLayout.scaleImage(
                imagen.getWidth(), imagen.getHeight(), foto.getAncho(),
                anchoInterior,
                ReportLayout.CONTENT_HEIGHT - altoDescripcion - ReportLayout.PHOTO_SPACING);
        return new PhotoPreviewData(
                foto, fotoIndex, imagen, altoDescripcion,
                tamanoNatural[1] + altoDescripcion + (ReportLayout.PHOTO_CELL_PADDING * 2));
    }

    private VBox crearCeldaFotoPreview(PhotoPreviewData datos, int categoriaIndex,
                                       int fotoIndex, double altoMaximoFila,
                                       double anchoCelda) {
        double altoMaximoImagen = Math.max(1,
                altoMaximoFila - datos.altoDescripcion - (ReportLayout.PHOTO_CELL_PADDING * 2));
        double[] tamano = ReportLayout.scaleImage(
                datos.imagen.getWidth(), datos.imagen.getHeight(),
                datos.foto.getAncho(),
                anchoCelda, altoMaximoImagen);

        VBox celda = new VBox(6);
        celda.setAlignment(Pos.TOP_LEFT);
        celda.setPrefWidth(anchoCelda);
        celda.setMinWidth(anchoCelda);
        celda.setMaxWidth(anchoCelda);
        celda.setStyle("-fx-background-color: transparent;");

        ImageView imageView = new ImageView(datos.imagen);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(tamano[0]);
        imageView.setFitHeight(tamano[1]);
        celda.getChildren().add(imageView);

        if (datos.altoDescripcion > 0) {
            Label descripcion = new Label(datos.foto.getEtiqueta().trim());
            aplicarEstiloTexto(descripcion, activeTemplate.getPhotoCommentStyle());
            descripcion.setWrapText(true);
            descripcion.setMaxWidth(anchoCelda);
            celda.getChildren().add(descripcion);
        }

        datos.altoReal = tamano[1] + datos.altoDescripcion
                + (ReportLayout.PHOTO_CELL_PADDING * 2);
        configurarArrastreFoto(celda, categoriaIndex, fotoIndex);
        return celda;
    }

    private void configurarArrastreFoto(VBox bloqueFoto, int categoriaOrigen, int fotoOrigen) {
        String estiloOriginal = bloqueFoto.getStyle();
        bloqueFoto.setOnDragDetected(event -> {
            Dragboard dragboard = bloqueFoto.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent contenido = new ClipboardContent();
            contenido.putString(DRAG_PHOTO_PREFIX + categoriaOrigen + ":" + fotoOrigen);
            dragboard.setContent(contenido);
            event.consume();
        });

        bloqueFoto.setOnDragOver(event -> {
            if (esDragValido(event.getDragboard())) {
                event.acceptTransferModes(event.getDragboard().hasFiles()
                        ? TransferMode.COPY : TransferMode.MOVE);
                boolean antes = event.getX() < bloqueFoto.getBoundsInLocal().getWidth() / 2.0;
                bloqueFoto.setStyle(estiloOriginal + (antes
                        ? "; -fx-border-color: transparent transparent transparent #2563eb; -fx-border-width: 0 0 0 3;"
                        : "; -fx-border-color: transparent #2563eb transparent transparent; -fx-border-width: 0 3 0 0;"));
            }
            event.consume();
        });

        bloqueFoto.setOnDragExited(event -> bloqueFoto.setStyle(estiloOriginal));
        bloqueFoto.setOnDragDropped(event -> {
            boolean antes = event.getX() < bloqueFoto.getBoundsInLocal().getWidth() / 2.0;
            int indiceInsercion = antes ? fotoOrigen : fotoOrigen + 1;
            boolean completado = procesarDrop(
                    event.getDragboard(), categoriaOrigen, indiceInsercion);
            event.setDropCompleted(completado);
            bloqueFoto.setStyle(estiloOriginal);
            event.consume();
        });
    }

    private void configurarDestinoDrop(Node destino, int categoriaDestino, int indiceInsercion) {
        String estiloOriginal = destino.getStyle();
        destino.setOnDragOver(event -> {
            if (esDragValido(event.getDragboard())) {
                event.acceptTransferModes(event.getDragboard().hasFiles()
                        ? TransferMode.COPY : TransferMode.MOVE);
                destino.setStyle(estiloOriginal
                        + "; -fx-border-color: #2563eb; -fx-border-width: 2px;");
            }
            event.consume();
        });
        destino.setOnDragExited(event -> destino.setStyle(estiloOriginal));
        destino.setOnDragDropped(event -> {
            boolean completado = procesarDrop(
                    event.getDragboard(), categoriaDestino, indiceInsercion);
            event.setDropCompleted(completado);
            destino.setStyle(estiloOriginal);
            event.consume();
        });
    }

    private boolean procesarDrop(Dragboard dragboard, int categoriaDestino, int indiceInsercion) {
        if (categoriaDestino < 0 || categoriaDestino >= categorias.size()) {
            return false;
        }

        CategoriaFotografica destino = categorias.get(categoriaDestino);
        if (dragboard.hasFiles()) {
            int agregadas = agregarArchivos(destino, indiceInsercion, dragboard.getFiles());
            if (agregadas == 0) {
                return false;
            }
            actualizarControlesFotos();
            actualizarPreview();
            return true;
        }

        if (dragboard.hasString() && dragboard.getString().startsWith(DRAG_PHOTO_PREFIX)) {
            String[] partes = dragboard.getString().substring(DRAG_PHOTO_PREFIX.length()).split(":");
            if (partes.length != 2) {
                return false;
            }
            try {
                int categoriaOrigen = Integer.parseInt(partes[0]);
                int fotoOrigen = Integer.parseInt(partes[1]);
                if (!PhotoOrder.move(categorias, categoriaOrigen, fotoOrigen,
                        categoriaDestino, indiceInsercion)) {
                    return false;
                }
                actualizarControlesFotos();
                actualizarPreview();
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private int agregarArchivos(CategoriaFotografica categoria, int indice, List<File> archivos) {
        int insertadas = 0;
        int posicion = Math.max(0, Math.min(indice, categoria.getFotografias().size()));
        for (File archivo : archivos) {
            if (esArchivoImagen(archivo)) {
                categoria.getFotografias().add(
                        posicion + insertadas,
                        new FotoEvidencia(archivo.getAbsolutePath(), ""));
                insertadas++;
            }
        }
        return insertadas;
    }

    private boolean esDragValido(Dragboard dragboard) {
        if (dragboard.hasString() && dragboard.getString().startsWith(DRAG_PHOTO_PREFIX)) {
            return true;
        }
        return dragboard.hasFiles() && dragboard.getFiles().stream().anyMatch(this::esArchivoImagen);
    }

    private boolean esArchivoImagen(File archivo) {
        if (archivo == null || !archivo.isFile()) {
            return false;
        }
        String nombre = archivo.getName().toLowerCase(Locale.ROOT);
        return nombre.endsWith(".png") || nombre.endsWith(".jpg")
                || nombre.endsWith(".jpeg");
    }

    private VBox crearHojaPaginaWord() {
        VBox hoja = new VBox();
        hoja.setPrefWidth(ReportLayout.PAGE_WIDTH);
        hoja.setMaxWidth(ReportLayout.PAGE_WIDTH);
        hoja.setMinWidth(ReportLayout.PAGE_WIDTH);
        hoja.setPrefHeight(ReportLayout.PAGE_HEIGHT);
        hoja.setMinHeight(ReportLayout.PAGE_HEIGHT);
        hoja.setMaxHeight(ReportLayout.PAGE_HEIGHT);
        hoja.setStyle("-fx-background-color: white; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 10, 0, 0, 3);");
        hoja.setPadding(new Insets(
                ReportLayout.MARGIN_VERTICAL,
                ReportLayout.MARGIN_HORIZONTAL,
                ReportLayout.MARGIN_VERTICAL,
                ReportLayout.MARGIN_HORIZONTAL));
        return hoja;
    }

    private VBox crearTablaReportePreview() {
        VBox tabla = new VBox();
        tabla.setPrefWidth(ReportLayout.CONTENT_WIDTH);
        tabla.setMaxWidth(ReportLayout.CONTENT_WIDTH);
        tabla.setStyle("-fx-border-color: black; -fx-border-width: 1px 1px 0 1px;");
        return tabla;
    }

    private void agregarSeccionPreview(VBox tabla, String titulo, String contenido) {
        Label encabezado = new Label(titulo);
        encabezado.setFont(Font.font("System", FontWeight.BOLD, 12));
        encabezado.setStyle("-fx-background-color: " + activeTemplate.getSectionBackgroundColor() + "; "
                + "-fx-border-color: transparent transparent black transparent; "
                + "-fx-border-width: 0 0 1 0;");
        encabezado.setPadding(new Insets(6, 10, 6, 10));
        encabezado.setWrapText(true);
        encabezado.setMaxWidth(Double.MAX_VALUE);

        Label texto = new Label(contenido);
        texto.setWrapText(true);
        texto.setPadding(new Insets(8, 10, 8, 10));
        texto.setMaxWidth(Double.MAX_VALUE);
        texto.setStyle("-fx-border-color: transparent transparent black transparent; "
                + "-fx-border-width: 0 0 1 0;");
        tabla.getChildren().addAll(encabezado, texto);
    }

    private HBox barraSeccionPreview(String texto) {
        Label lbl = new Label(texto);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 12));
        lbl.setStyle("-fx-background-color: " + activeTemplate.getSectionBackgroundColor() + "; "
                + "-fx-border-color: transparent transparent black transparent; "
                + "-fx-border-width: 0 0 1 0;");
        lbl.setPadding(new Insets(6, 10, 6, 10));
        lbl.setWrapText(true);
        lbl.setPrefWidth(ReportLayout.CONTENT_WIDTH);
        lbl.setMaxWidth(ReportLayout.CONTENT_WIDTH);
        lbl.setMinWidth(ReportLayout.CONTENT_WIDTH);

        HBox contenedor = new HBox(lbl);
        contenedor.setPrefWidth(ReportLayout.CONTENT_WIDTH);
        contenedor.setMaxWidth(ReportLayout.CONTENT_WIDTH);
        contenedor.setMinWidth(ReportLayout.CONTENT_WIDTH);
        return contenedor;
    }

    private void agregarFilaTabla(GridPane tabla, int fila, String etiqueta, String valor,
                                  String backgroundColor) {
        Label lblEtiqueta = new Label(etiqueta);
        lblEtiqueta.setFont(Font.font("System", FontWeight.BOLD, 12));
        lblEtiqueta.setStyle("-fx-background-color: " + backgroundColor + "; -fx-border-color: #999999; "
                + "-fx-border-width: 0.5px;");
        lblEtiqueta.setPadding(new Insets(6, 10, 6, 10));
        lblEtiqueta.setMaxWidth(Double.MAX_VALUE);

        Label lblValor = new Label(valorOVacio(valor));
        lblValor.setFont(Font.font("System", FontWeight.BOLD, 12));
        lblValor.setStyle("-fx-background-color: white; -fx-border-color: #999999; "
                + "-fx-border-width: 0.5px;");
        lblValor.setPadding(new Insets(6, 10, 6, 10));
        lblValor.setMaxWidth(Double.MAX_VALUE);

        tabla.add(lblEtiqueta, 0, fila);
        tabla.add(lblValor, 1, fila);
    }

    private String getFieldValue(String key) {
        return switch (key) {
            case "fecha" -> dpFecha.getValue() == null ? "" : dpFecha.getValue().format(FORMATO_FECHA);
            case "area" -> txtArea.getText();
            case "remision" -> txtRemision.getText();
            case "cotizacion" -> txtCotizacion.getText();
            case "factura" -> txtFactura.getText();
            default -> customValueControls.containsKey(key)
                    ? customValueControls.get(key).getText() : "";
        };
    }

    private void aplicarEstiloTexto(Label label, TextStyle style) {
        FontWeight weight = style.isBold() ? FontWeight.BOLD : FontWeight.NORMAL;
        FontPosture posture = style.isItalic() ? FontPosture.ITALIC : FontPosture.REGULAR;
        label.setFont(Font.font(style.getFontFamily(), weight, posture, style.getFontSize()));
        try { label.setTextFill(Color.web(style.getColor())); }
        catch (Exception ignored) { label.setTextFill(Color.DARKSLATEGRAY); }
    }

    private Image cargarImagenEncabezado() {
        try {
            if (!activeTemplate.getHeaderImageBase64().isBlank()) {
                return new Image(new ByteArrayInputStream(Base64.getDecoder().decode(
                        activeTemplate.getHeaderImageBase64())));
            }
        } catch (Exception ignored) {}
        return new Image(App.class.getResource("Imagen12.jpg").toExternalForm());
    }

    private String valorOVacio(String texto) {
        return (texto == null || texto.trim().isEmpty()) ? "—" : texto.trim();
    }

    private String valorSinNulo(String texto) {
        return texto == null ? "" : texto;
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        aplicarTema(alert);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void aplicarTema(Dialog<?> dialog) {
        App.applyTheme(dialog.getDialogPane());
    }

    private static class PreviewPageState {
        private final VBox pagina;
        private final VBox tabla;
        private double espacioDisponible;

        private PreviewPageState(VBox pagina, VBox tabla, double espacioDisponible) {
            this.pagina = pagina;
            this.tabla = tabla;
            this.espacioDisponible = espacioDisponible;
        }
    }

    private static class PhotoPreviewData {
        private final FotoEvidencia foto;
        private final int fotoIndex;
        private final Image imagen;
        private final double altoDescripcion;
        private final double altoNatural;
        private double altoReal;

        private PhotoPreviewData(FotoEvidencia foto, int fotoIndex, Image imagen,
                                 double altoDescripcion, double altoNatural) {
            this.foto = foto;
            this.fotoIndex = fotoIndex;
            this.imagen = imagen;
            this.altoDescripcion = altoDescripcion;
            this.altoNatural = altoNatural;
        }
    }
}
