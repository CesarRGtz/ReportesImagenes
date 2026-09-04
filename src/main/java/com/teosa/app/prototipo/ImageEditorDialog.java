package com.teosa.app.prototipo;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class ImageEditorDialog {

    private static final double MAX_DISPLAY_WIDTH = 860;
    private static final double MAX_DISPLAY_HEIGHT = 520;

    private ImageEditorDialog() {
    }

    static Optional<Path> mostrar(Window owner, Path source, Path outputDirectory)
            throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("No se encontró la fotografía que deseas editar.");
        }

        Image image = new Image(source.toUri().toString());
        if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("El formato de la imagen no pudo abrirse.");
        }

        double scale = Math.min(MAX_DISPLAY_WIDTH / image.getWidth(),
                MAX_DISPLAY_HEIGHT / image.getHeight());
        scale = Math.min(1.0, scale);
        double displayWidth = Math.max(1, image.getWidth() * scale);
        double displayHeight = Math.max(1, image.getHeight() * scale);

        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(displayWidth);
        imageView.setFitHeight(displayHeight);
        imageView.setPreserveRatio(false);

        Pane annotationLayer = fixedPane(displayWidth, displayHeight);
        annotationLayer.setMouseTransparent(true);
        Rectangle cropSelection = new Rectangle(0, 0, displayWidth, displayHeight);
        cropSelection.setFill(Color.color(0.15, 0.45, 0.95, 0.12));
        cropSelection.setStroke(Color.web("#2563eb"));
        cropSelection.setStrokeWidth(2);
        cropSelection.setMouseTransparent(true);

        Pane surface = fixedPane(displayWidth, displayHeight);
        surface.setStyle("-fx-background-color: #111827; -fx-border-color: #94a3b8;");
        surface.getChildren().addAll(imageView, annotationLayer, cropSelection);

        ToggleGroup toolGroup = new ToggleGroup();
        ToggleButton cropTool = toolButton("Recortar", Tool.CROP, toolGroup);
        ToggleButton arrowTool = toolButton("Flecha", Tool.ARROW, toolGroup);
        ToggleButton circleTool = toolButton("Círculo", Tool.CIRCLE, toolGroup);
        ToggleButton crossTool = toolButton("Equis", Tool.CROSS, toolGroup);
        ToggleButton checkTool = toolButton("Palomita", Tool.CHECK, toolGroup);
        cropTool.setSelected(true);
        toolGroup.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected == null) {
                cropTool.setSelected(true);
                return;
            }
            cropSelection.setVisible(selected.getUserData() == Tool.CROP);
        });

        ColorPicker colorPicker = new ColorPicker(Color.web("#ef4444"));
        colorPicker.setAccessibleText("Color de la anotación");
        Slider strokeSlider = new Slider(2, 16, 5);
        strokeSlider.setPrefWidth(135);
        strokeSlider.setShowTickMarks(true);
        strokeSlider.setMajorTickUnit(7);
        Label strokeValue = new Label("5 px");
        strokeSlider.valueProperty().addListener((obs, oldValue, newValue) ->
                strokeValue.setText(Math.round(newValue.doubleValue()) + " px"));

        List<Mark> marks = new ArrayList<>();
        Button undo = new Button("Deshacer marca");
        undo.setDisable(true);
        Button clear = new Button("Quitar marcas");
        clear.setDisable(true);
        Runnable redraw = () -> {
            annotationLayer.getChildren().clear();
            for (Mark mark : marks) {
                annotationLayer.getChildren().add(createVisual(mark));
            }
            undo.setDisable(marks.isEmpty());
            clear.setDisable(marks.isEmpty());
        };
        undo.setOnAction(event -> {
            if (!marks.isEmpty()) {
                marks.remove(marks.size() - 1);
                redraw.run();
            }
        });
        clear.setOnAction(event -> {
            marks.clear();
            redraw.run();
        });

        Button selectAll = new Button("Usar imagen completa");
        selectAll.setOnAction(event -> setCrop(cropSelection,
                0, 0, displayWidth, displayHeight));

        Label help = new Label(
                "Elige una herramienta y arrastra sobre la imagen. "
                        + "Al arrastrar defines el tamaño de la figura.");
        help.setWrapText(true);

        final double[] start = new double[2];
        final Node[] temporaryVisual = new Node[1];
        surface.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            start[0] = clamp(event.getX(), 0, displayWidth);
            start[1] = clamp(event.getY(), 0, displayHeight);
            Tool tool = selectedTool(toolGroup);
            if (tool == Tool.CROP) {
                setCrop(cropSelection, start[0], start[1], 0, 0);
            } else {
                removeTemporary(annotationLayer, temporaryVisual);
            }
        });
        surface.setOnMouseDragged(event -> {
            double x = clamp(event.getX(), 0, displayWidth);
            double y = clamp(event.getY(), 0, displayHeight);
            Tool tool = selectedTool(toolGroup);
            if (tool == Tool.CROP) {
                setCrop(cropSelection, Math.min(start[0], x), Math.min(start[1], y),
                        Math.abs(x - start[0]), Math.abs(y - start[1]));
                return;
            }
            removeTemporary(annotationLayer, temporaryVisual);
            Mark preview = normalizedMark(tool, start[0], start[1], x, y,
                    colorPicker.getValue(), strokeSlider.getValue(),
                    displayWidth, displayHeight);
            if (preview != null) {
                temporaryVisual[0] = createVisual(preview);
                annotationLayer.getChildren().add(temporaryVisual[0]);
            }
        });
        surface.setOnMouseReleased(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            Tool tool = selectedTool(toolGroup);
            if (tool == Tool.CROP) return;
            double x = clamp(event.getX(), 0, displayWidth);
            double y = clamp(event.getY(), 0, displayHeight);
            removeTemporary(annotationLayer, temporaryVisual);
            Mark mark = normalizedMark(tool, start[0], start[1], x, y,
                    colorPicker.getValue(), strokeSlider.getValue(),
                    displayWidth, displayHeight);
            if (mark != null) {
                marks.add(mark);
                redraw.run();
            }
        });

        FlowPane tools = new FlowPane(7, 7,
                cropTool, arrowTool, circleTool, crossTool, checkTool);
        tools.setAlignment(Pos.CENTER_LEFT);
        HBox appearance = new HBox(8,
                new Label("Color:"), colorPicker,
                new Label("Grosor:"), strokeSlider, strokeValue);
        appearance.setAlignment(Pos.CENTER_LEFT);
        FlowPane actions = new FlowPane(8, 8, selectAll, undo, clear);
        actions.setAlignment(Pos.CENTER_LEFT);

        ScrollPane imageScroll = new ScrollPane(surface);
        imageScroll.setFitToWidth(false);
        imageScroll.setFitToHeight(false);
        imageScroll.setPannable(false);
        imageScroll.setMaxHeight(MAX_DISPLAY_HEIGHT + 4);
        imageScroll.setStyle("-fx-background-color: transparent;");

        VBox content = new VBox(10, tools, appearance, help, imageScroll, actions);
        content.setPadding(new Insets(2));

        Dialog<ButtonType> dialog = new Dialog<>();
        App.applyTheme(dialog.getDialogPane());
        dialog.setTitle("Editar imagen");
        dialog.setHeaderText("Recorta o agrega indicaciones sobre la fotografía");
        ButtonType apply = new ButtonType("Guardar edición", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(apply, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(960, 720);
        if (owner != null) dialog.initOwner(owner);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != apply) return Optional.empty();
        if (cropSelection.getWidth() < 3 || cropSelection.getHeight() < 3) {
            throw new IOException("El área seleccionada para el recorte es demasiado pequeña.");
        }

        Files.createDirectories(outputDirectory);
        Path output = outputDirectory.resolve("edicion-" + UUID.randomUUID() + ".png");
        render(source, output, marks, cropSelection, displayWidth, displayHeight);
        return Optional.of(output);
    }

    private static Pane fixedPane(double width, double height) {
        Pane pane = new Pane();
        pane.setPrefSize(width, height);
        pane.setMinSize(width, height);
        pane.setMaxSize(width, height);
        return pane;
    }

    private static ToggleButton toolButton(String text, Tool tool, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.setUserData(tool);
        button.setToggleGroup(group);
        return button;
    }

    private static Tool selectedTool(ToggleGroup group) {
        Toggle selected = group.getSelectedToggle();
        if (selected == null || !(selected.getUserData() instanceof Tool tool)) {
            return Tool.CROP;
        }
        return tool;
    }

    private static void setCrop(Rectangle crop, double x, double y,
                                double width, double height) {
        crop.setX(x);
        crop.setY(y);
        crop.setWidth(width);
        crop.setHeight(height);
    }

    private static void removeTemporary(Pane layer, Node[] temporary) {
        if (temporary[0] != null) {
            layer.getChildren().remove(temporary[0]);
            temporary[0] = null;
        }
    }

    private static Mark normalizedMark(Tool tool, double x1, double y1,
                                       double x2, double y2, Color color, double stroke,
                                       double maxWidth, double maxHeight) {
        if (tool == Tool.ARROW) {
            if (Math.hypot(x2 - x1, y2 - y1) < 5) return null;
            return new Mark(tool, x1, y1, x2, y2, color, stroke);
        }
        if (tool == Tool.CROP) return null;

        double minimum = Math.max(18, stroke * 5);
        if (Math.abs(x2 - x1) < 8 && Math.abs(y2 - y1) < 8) {
            double half = minimum / 2;
            x1 = clamp(x1 - half, 0, maxWidth);
            y1 = clamp(y1 - half, 0, maxHeight);
            x2 = clamp(x1 + minimum, 0, maxWidth);
            y2 = clamp(y1 + minimum, 0, maxHeight);
        }
        return new Mark(tool, x1, y1, x2, y2, color, stroke);
    }

    private static Node createVisual(Mark mark) {
        double left = Math.min(mark.x1, mark.x2);
        double top = Math.min(mark.y1, mark.y2);
        double width = Math.abs(mark.x2 - mark.x1);
        double height = Math.abs(mark.y2 - mark.y1);

        return switch (mark.tool) {
            case ARROW -> arrowVisual(mark);
            case CIRCLE -> {
                Ellipse ellipse = new Ellipse(left + width / 2, top + height / 2,
                        width / 2, height / 2);
                styleOutline(ellipse, mark);
                yield ellipse;
            }
            case CROSS -> {
                Line first = styledLine(left, top, left + width, top + height, mark);
                Line second = styledLine(left + width, top, left, top + height, mark);
                yield new Group(first, second);
            }
            case CHECK -> {
                Polyline check = new Polyline(
                        left, top + height * 0.58,
                        left + width * 0.38, top + height,
                        left + width, top);
                styleOutline(check, mark);
                yield check;
            }
            case CROP -> new Group();
        };
    }

    private static Node arrowVisual(Mark mark) {
        Line shaft = styledLine(mark.x1, mark.y1, mark.x2, mark.y2, mark);
        double angle = Math.atan2(mark.y2 - mark.y1, mark.x2 - mark.x1);
        double head = Math.max(12, mark.stroke * 3.5);
        double spread = Math.PI / 7;
        double ax = mark.x2 - head * Math.cos(angle - spread);
        double ay = mark.y2 - head * Math.sin(angle - spread);
        double bx = mark.x2 - head * Math.cos(angle + spread);
        double by = mark.y2 - head * Math.sin(angle + spread);
        Polygon tip = new Polygon(mark.x2, mark.y2, ax, ay, bx, by);
        tip.setFill(mark.color);
        return new Group(shaft, tip);
    }

    private static Line styledLine(double x1, double y1, double x2, double y2, Mark mark) {
        Line line = new Line(x1, y1, x2, y2);
        styleOutline(line, mark);
        return line;
    }

    private static void styleOutline(javafx.scene.shape.Shape shape, Mark mark) {
        shape.setFill(Color.TRANSPARENT);
        shape.setStroke(mark.color);
        shape.setStrokeWidth(mark.stroke);
        shape.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        shape.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
    }

    private static void render(Path source, Path output, List<Mark> marks,
                               Rectangle crop, double displayWidth, double displayHeight)
            throws IOException {
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) throw new IOException("El formato de la imagen no es compatible.");

        BufferedImage annotated = new BufferedImage(original.getWidth(), original.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = annotated.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original, 0, 0, null);
            double scaleX = original.getWidth() / displayWidth;
            double scaleY = original.getHeight() / displayHeight;
            double strokeScale = (scaleX + scaleY) / 2;
            for (Mark mark : marks) {
                drawMark(graphics, mark, scaleX, scaleY, strokeScale);
            }
        } finally {
            graphics.dispose();
        }

        int x = clampInt((int) Math.round(crop.getX() / displayWidth * original.getWidth()),
                0, original.getWidth() - 1);
        int y = clampInt((int) Math.round(crop.getY() / displayHeight * original.getHeight()),
                0, original.getHeight() - 1);
        int width = clampInt(
                (int) Math.round(crop.getWidth() / displayWidth * original.getWidth()),
                1, original.getWidth() - x);
        int height = clampInt(
                (int) Math.round(crop.getHeight() / displayHeight * original.getHeight()),
                1, original.getHeight() - y);

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D croppedGraphics = result.createGraphics();
        try {
            croppedGraphics.drawImage(annotated, -x, -y, null);
        } finally {
            croppedGraphics.dispose();
        }
        if (!ImageIO.write(result, "png", output.toFile())) {
            throw new IOException("No fue posible guardar la imagen editada.");
        }
    }

    private static void drawMark(Graphics2D graphics, Mark mark,
                                 double scaleX, double scaleY, double strokeScale) {
        double x1 = mark.x1 * scaleX;
        double y1 = mark.y1 * scaleY;
        double x2 = mark.x2 * scaleX;
        double y2 = mark.y2 * scaleY;
        float stroke = (float) Math.max(1, mark.stroke * strokeScale);
        graphics.setColor(new java.awt.Color(
                (float) mark.color.getRed(), (float) mark.color.getGreen(),
                (float) mark.color.getBlue(), (float) mark.color.getOpacity()));
        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));

        double left = Math.min(x1, x2);
        double top = Math.min(y1, y2);
        double width = Math.abs(x2 - x1);
        double height = Math.abs(y2 - y1);
        switch (mark.tool) {
            case ARROW -> {
                graphics.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
                double angle = Math.atan2(y2 - y1, x2 - x1);
                double head = Math.max(12 * strokeScale, stroke * 3.5);
                double spread = Math.PI / 7;
                Path2D tip = new Path2D.Double();
                tip.moveTo(x2, y2);
                tip.lineTo(x2 - head * Math.cos(angle - spread),
                        y2 - head * Math.sin(angle - spread));
                tip.lineTo(x2 - head * Math.cos(angle + spread),
                        y2 - head * Math.sin(angle + spread));
                tip.closePath();
                graphics.fill(tip);
            }
            case CIRCLE -> graphics.draw(new Ellipse2D.Double(left, top, width, height));
            case CROSS -> {
                graphics.draw(new java.awt.geom.Line2D.Double(left, top,
                        left + width, top + height));
                graphics.draw(new java.awt.geom.Line2D.Double(left + width, top,
                        left, top + height));
            }
            case CHECK -> {
                Path2D check = new Path2D.Double();
                check.moveTo(left, top + height * 0.58);
                check.lineTo(left + width * 0.38, top + height);
                check.lineTo(left + width, top);
                graphics.draw(check);
            }
            case CROP -> {
                // El recorte se aplica después de dibujar las anotaciones.
            }
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clampInt(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum Tool {
        CROP, ARROW, CIRCLE, CROSS, CHECK
    }

    private record Mark(Tool tool, double x1, double y1, double x2, double y2,
                        Color color, double stroke) {
    }
}
