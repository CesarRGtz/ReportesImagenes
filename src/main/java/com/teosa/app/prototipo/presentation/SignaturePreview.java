package com.teosa.app.prototipo.presentation;
import com.teosa.app.prototipo.domain.*;
import static com.teosa.app.prototipo.domain.CuadroFirmas.*;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import java.util.Base64;
import java.io.ByteArrayInputStream;
public final class SignaturePreview {
    public static VBox preview(FirmasReporte f) {
        VBox box = new VBox();
        String[][] rows = {{f.getImagenElaboro(), f.getImagenVerifico()},
                {"Elaboró", "Verificó"}, {"Supervisor", "Responsable"},
                {f.getNombreElaboro(), f.getNombreVerifico()}};
        double[] heights = {112, 22, 32, altoNombre(f)};
        for (int row = 0; row < rows.length; row++) {
            HBox line = new HBox();
            for (String value : rows[row]) {
                StackPane cell = new StackPane();
                cell.setPrefWidth(ANCHO / 2);
                cell.setMinWidth(ANCHO / 2);
                cell.setMinHeight(heights[row]); cell.setPrefHeight(heights[row]);
                cell.setStyle("-fx-border-color: black; -fx-border-width: 0.75; -fx-padding: 3;");
                if (row == 0 && !value.isBlank()) {
                    ImageView img = new ImageView(new javafx.scene.image.Image(new ByteArrayInputStream(Base64.getDecoder().decode(value))));
                    img.setPreserveRatio(true); img.setFitWidth(ANCHO / 2 - 16); img.setFitHeight(96);
                    cell.getChildren().add(img);
                } else if (row != 0) {
                    Label label = new Label(value); label.setWrapText(true);
                    label.setAlignment(Pos.CENTER); label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
                    label.setStyle("-fx-font-family: Arial; -fx-font-size: 11px; -fx-text-fill: black;");
                    cell.getChildren().add(label);
                }
                line.getChildren().add(cell);
            }
            box.getChildren().add(line);
        }
        box.setMinHeight(alto(f)); box.setPrefHeight(alto(f));
        box.setMaxHeight(alto(f));
        box.setMinWidth(ANCHO); box.setPrefWidth(ANCHO); box.setMaxWidth(ANCHO);
        return box;
    }
}
