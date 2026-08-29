package com.teosa.app.prototipo;

import javafx.application.Platform;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PrimaryController {

    private static final String DRAG_PHOTO_PREFIX = "TEOSA_PHOTO:";
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
    @FXML private ScrollPane formScrollPane;
    @FXML private VBox photoControls;
    @FXML private VBox panePreview;

    private final List<CategoriaFotografica> categorias = new ArrayList<>();
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

        txtCliente.textProperty().addListener((o, a, b) -> actualizarPreview());
        dpFecha.valueProperty().addListener((o, a, b) -> actualizarPreview());
        txtArea.textProperty().addListener((o, a, b) -> actualizarPreview());
        txtRemision.textProperty().addListener((o, a, b) -> actualizarPreview());
        txtCotizacion.textProperty().addListener((o, a, b) -> actualizarPreview());
        txtFactura.textProperty().addListener((o, a, b) -> actualizarPreview());
        txtDatosEquipo.textProperty().addListener((o, a, b) -> actualizarPreview());
        txtDescripcion.textProperty().addListener((o, a, b) -> actualizarPreview());

        actualizarControlesFotos();
        actualizarPreview();
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

    @FXML
    private void handleClicAccion() {
        String cliente = txtCliente.getText();
        String fecha = dpFecha.getValue() != null ? dpFecha.getValue().format(FORMATO_FECHA) : "";

        if (cliente == null || cliente.trim().isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Falta información",
                    "Debes capturar el nombre del cliente/empresa.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar reporte PDF");
        fileChooser.setInitialFileName("Reporte_" + cliente.replaceAll("\\s+", "_") + ".pdf");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo PDF", "*.pdf"));
        File destino = fileChooser.showSaveDialog(btnGenerarPDF.getScene().getWindow());
        if (destino == null) {
            return;
        }

        ReporteServicio reporte = new ReporteServicio(
                cliente, fecha, txtArea.getText(), txtRemision.getText(),
                txtCotizacion.getText(), txtFactura.getText(),
                txtDatosEquipo.getText(), txtDescripcion.getText());

        for (CategoriaFotografica categoria : categorias) {
            CategoriaFotografica copiaCategoria = new CategoriaFotografica(categoria.getTitulo());
            for (FotoEvidencia foto : categoria.getFotografias()) {
                FotoEvidencia copiaFoto = new FotoEvidencia(foto.getRuta(), foto.getEtiqueta());
                copiaFoto.setAncho(foto.getAncho());
                copiaCategoria.agregarFotografia(copiaFoto);
            }
            reporte.agregarCategoriaFotografica(copiaCategoria);
        }

        try {
            PdfReportGenerator.generar(destino, reporte);
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
        Printer printer = Printer.getDefaultPrinter();
        if (printer == null) {
            mostrarAlerta(Alert.AlertType.ERROR, "Sin impresora",
                    "No se detectó ninguna impresora predeterminada.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job != null && job.showPrintDialog(btnImprimir.getScene().getWindow())) {
            boolean printed = job.printPage(panePreview);
            if (printed) {
                job.endJob();
                mostrarAlerta(Alert.AlertType.INFORMATION, "Impresión exitosa",
                        "El documento se envió a la impresora.");
            } else {
                mostrarAlerta(Alert.AlertType.ERROR, "Error de impresión",
                        "No se pudo completar la impresión.");
            }
        }
    }

    private void actualizarPreview() {
        panePreview.getChildren().clear();
        panePreview.setSpacing(25);

        VBox pagina1 = crearHojaPaginaWord();
        String empresa = valorOVacio(txtCliente.getText()).equals("—")
                ? "" : txtCliente.getText().trim().toUpperCase();
        Label titulo = new Label("Reporte de Servicio Elaborado para\n"
                + (empresa.isEmpty() ? "____________" : empresa));
        titulo.setFont(Font.font("Arial", FontWeight.BOLD, FontPosture.ITALIC, 14));
        titulo.setTextFill(Color.web("#5b7699"));
        titulo.setWrapText(true);
        titulo.setAlignment(Pos.CENTER);
        titulo.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        ImageView logo = new ImageView(new Image(
                App.class.getResource("Imagen12.jpg").toExternalForm()));
        logo.setPreserveRatio(true);
        logo.setFitWidth(135);
        logo.setFitHeight(87);

        HBox encabezadoDocumento = new HBox(18, logo, titulo);
        encabezadoDocumento.setAlignment(Pos.CENTER);
        encabezadoDocumento.setPrefWidth(ReportLayout.CONTENT_WIDTH);
        encabezadoDocumento.setMaxWidth(ReportLayout.CONTENT_WIDTH);
        HBox.setHgrow(titulo, Priority.ALWAYS);
        VBox.setMargin(encabezadoDocumento, new Insets(0, 0, 5, 0));
        pagina1.getChildren().add(encabezadoDocumento);

        javafx.scene.shape.Line linea = new javafx.scene.shape.Line(
                0, 0, ReportLayout.CONTENT_WIDTH, 0);
        linea.setStroke(Color.web("#8ca3bf"));
        linea.setStrokeWidth(0.5);
        VBox.setMargin(linea, new Insets(5, 0, 15, 0));
        pagina1.getChildren().add(linea);

        GridPane tabla = new GridPane();
        tabla.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(38);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(62);
        tabla.getColumnConstraints().addAll(col1, col2);

        String fechaTexto = dpFecha.getValue() != null
                ? dpFecha.getValue().format(FORMATO_FECHA) : "";
        agregarFilaTabla(tabla, 0, "Fecha de Recepción de Equipo:", fechaTexto);
        agregarFilaTabla(tabla, 1, "Área:", txtArea.getText());
        agregarFilaTabla(tabla, 2, "Remisión:", txtRemision.getText());
        agregarFilaTabla(tabla, 3, "Cotización:", txtCotizacion.getText());
        agregarFilaTabla(tabla, 4, "Factura:", txtFactura.getText());
        pagina1.getChildren().add(tabla);

        VBox tablaReporte = crearTablaReportePreview();
        VBox.setMargin(tablaReporte, new Insets(10, 0, 0, 0));
        agregarSeccionPreview(tablaReporte,
                "1.  DATOS DEL EQUIPO:", valorOVacio(txtDatosEquipo.getText()));
        agregarSeccionPreview(tablaReporte,
                "2.  DESCRIPCIÓN DEL TRABAJO:", valorOVacio(txtDescripcion.getText()));
        pagina1.getChildren().add(tablaReporte);

        panePreview.getChildren().add(pagina1);
        double espacioDisponible = ReportLayout.initialPhotoSpace(
                txtDatosEquipo.getText(), txtDescripcion.getText());
        agregarPaginasFotosPreview(new PreviewPageState(
                pagina1, tablaReporte, Math.max(0, espacioDisponible)));
    }

    private void actualizarControlesFotos() {
        double posicionScroll = formScrollPane == null ? 0 : formScrollPane.getVvalue();
        photoControls.getChildren().clear();

        for (int categoriaIndex = 0; categoriaIndex < categorias.size(); categoriaIndex++) {
            CategoriaFotografica categoria = categorias.get(categoriaIndex);
            VBox tarjetaCategoria = new VBox(8);
            tarjetaCategoria.setPadding(new Insets(10));
            tarjetaCategoria.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e0; "
                    + "-fx-border-radius: 5px; -fx-background-radius: 5px;");

            Label tituloControl = new Label("Categoría " + (categoriaIndex + 1));
            tituloControl.setFont(Font.font("System", FontWeight.BOLD, 11));
            tituloControl.setTextFill(Color.web("#2b6cb0"));

            Button eliminarCategoria = new Button("Eliminar categoría");
            eliminarCategoria.setStyle("-fx-text-fill: #b91c1c;");
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

            Button agregarFotos = new Button("Agregar imágenes a esta categoría");
            agregarFotos.setMaxWidth(Double.MAX_VALUE);
            agregarFotos.setOnAction(event -> seleccionarFotosParaCategoria(categoria));
            tarjetaCategoria.getChildren().addAll(encabezadoControl, tituloCategoria, agregarFotos);

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
                eliminarFoto.setStyle("-fx-text-fill: #b91c1c;");
                eliminarFoto.setOnAction(event -> {
                    categoria.getFotografias().remove(foto);
                    actualizarControlesFotos();
                    actualizarPreview();
                });

                HBox filaAncho = new HBox(8, etiquetaAncho, sliderAncho, eliminarFoto);
                filaAncho.setAlignment(Pos.CENTER_LEFT);

                TextArea detalle = new TextArea(valorSinNulo(foto.getEtiqueta()));
                detalle.setPromptText("Descripción detallada opcional de esta imagen");
                detalle.setPrefRowCount(2);
                detalle.setWrapText(true);
                detalle.textProperty().addListener((obs, oldVal, newVal) -> {
                    foto.setEtiqueta(newVal);
                    actualizarPreview();
                });

                VBox controlesFoto = new VBox(5, filaAncho, detalle);
                controlesFoto.setPadding(new Insets(7, 0, 7, 8));
                controlesFoto.setStyle("-fx-border-color: transparent transparent #e2e8f0 transparent;");
                tarjetaCategoria.getChildren().add(controlesFoto);
            }

            photoControls.getChildren().add(tarjetaCategoria);
        }

        if (formScrollPane != null) {
            Platform.runLater(() -> formScrollPane.setVvalue(posicionScroll));
        }
    }

    private void agregarPaginasFotosPreview(PreviewPageState estado) {
        if (categorias.isEmpty()) {
            return;
        }

        if (ReportLayout.PHOTO_SECTION_HEIGHT > estado.espacioDisponible) {
            estado = crearPaginaFotos(false);
        } else {
            estado.tabla.getChildren().add(barraSeccionPreview(
                    "3.  REPORTE FOTOGRÁFICO DEL ANTES, DURANTE Y DESPUÉS DE REALIZAR EL TRABAJO:"));
            estado.espacioDisponible -= ReportLayout.PHOTO_SECTION_HEIGHT;
        }

        for (int categoriaIndex = 0; categoriaIndex < categorias.size(); categoriaIndex++) {
            CategoriaFotografica categoria = categorias.get(categoriaIndex);
            String tituloCategoria = valorOVacio(categoria.getTitulo());
            double altoMinimoCategoria = ReportLayout.estimateCategoryTitleHeight(tituloCategoria)
                    + estimarPrimeraFila(categoria);
            if (altoMinimoCategoria > estado.espacioDisponible) {
                estado = crearPaginaFotos(true);
            }

            Label encabezadoCategoria = crearTituloCategoriaPreview(tituloCategoria);
            configurarDestinoDrop(
                    encabezadoCategoria, categoriaIndex, categoria.getFotografias().size());
            estado.tabla.getChildren().add(encabezadoCategoria);
            estado.espacioDisponible -= ReportLayout.estimateCategoryTitleHeight(tituloCategoria);

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
                    estado = crearPaginaFotos(true);
                    Label continuacion = crearTituloCategoriaPreview(tituloCategoria + " (continuación)");
                    configurarDestinoDrop(continuacion, categoriaIndex, inicioFila);
                    estado.tabla.getChildren().add(continuacion);
                    estado.espacioDisponible -= ReportLayout.estimateCategoryTitleHeight(
                            tituloCategoria + " (continuación)");
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

    private PreviewPageState crearPaginaFotos(boolean continuacion) {
        VBox pagina = crearHojaPaginaWord();
        VBox tablaFotos = crearTablaReportePreview();
        pagina.getChildren().add(tablaFotos);
        double espacioDisponible = ReportLayout.CONTENT_HEIGHT;
        HBox encabezado = barraSeccionPreview(
                continuacion
                        ? "3.  REPORTE FOTOGRÁFICO (CONTINUACIÓN):"
                        : "3.  REPORTE FOTOGRÁFICO DEL ANTES, DURANTE Y DESPUÉS DE REALIZAR EL TRABAJO:");
        tablaFotos.getChildren().add(encabezado);
        espacioDisponible -= ReportLayout.PHOTO_SECTION_HEIGHT;
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
        encabezado.setFont(Font.font("System", FontWeight.BOLD, 14));
        encabezado.setTextFill(Color.web("#1f4e79"));
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
                foto.getEtiqueta(), anchoInterior);
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
            descripcion.setFont(Font.font("System", FontPosture.ITALIC, 11));
            descripcion.setTextFill(Color.web("#334155"));
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
        encabezado.setStyle("-fx-background-color: #bfbfbf; "
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
        lbl.setStyle("-fx-background-color: #bfbfbf; "
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

    private void agregarFilaTabla(GridPane tabla, int fila, String etiqueta, String valor) {
        Label lblEtiqueta = new Label(etiqueta);
        lblEtiqueta.setFont(Font.font("System", FontWeight.BOLD, 12));
        lblEtiqueta.setStyle("-fx-background-color: #d9d9d9; -fx-border-color: #999999; "
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

    private String valorOVacio(String texto) {
        return (texto == null || texto.trim().isEmpty()) ? "—" : texto.trim();
    }

    private String valorSinNulo(String texto) {
        return texto == null ? "" : texto;
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
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
