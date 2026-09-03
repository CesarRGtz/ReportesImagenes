package com.teosa.app.prototipo;

import com.teosa.app.prototipo.data.AppConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.Optional;

public final class RoleConfigurationDialog {
    private static final String SELECTED_STYLE = "-fx-background-color: #eef2ff; "
            + "-fx-border-color: #6366f1; -fx-border-width: 2px; "
            + "-fx-background-radius: 10px; -fx-border-radius: 10px; -fx-cursor: hand;";
    private static final String NORMAL_STYLE = "-fx-background-color: white; "
            + "-fx-border-color: #dbe3ee; -fx-border-width: 1px; "
            + "-fx-background-radius: 10px; -fx-border-radius: 10px; -fx-cursor: hand;";

    private RoleConfigurationDialog() {}

    public static Optional<AppConfig.Role> show(
            Window owner, AppConfig.Role currentRole, boolean initialSetup) {
        Dialog<AppConfig.Role> dialog = new Dialog<>();
        dialog.setTitle(initialSetup ? "Configuración inicial" : "Configuración del equipo");
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);

        ButtonType confirm = new ButtonType(
                initialSetup ? "Continuar" : "Guardar cambio", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(confirm);
        if (!initialSetup) dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        Label eyebrow = new Label(initialSetup ? "PRIMER INICIO" : "CONEXIÓN DEL SISTEMA");
        eyebrow.setTextFill(Color.web("#4f46e5"));
        eyebrow.setFont(Font.font("System", FontWeight.BOLD, 10));

        Label title = new Label("¿Qué función tendrá esta computadora?");
        title.setTextFill(Color.web("#172033"));
        title.setFont(Font.font("System", FontWeight.BOLD, 20));
        title.setWrapText(true);

        Label subtitle = new Label(
                "Elige cómo participará este equipo en la red local de Reportes TEOSA.");
        subtitle.setTextFill(Color.web("#64748b"));
        subtitle.setWrapText(true);

        ToggleGroup roles = new ToggleGroup();
        RadioButton primaryToggle = new RadioButton();
        primaryToggle.setToggleGroup(roles);
        primaryToggle.setUserData(AppConfig.Role.PRIMARY);
        RadioButton secondaryToggle = new RadioButton();
        secondaryToggle.setToggleGroup(roles);
        secondaryToggle.setUserData(AppConfig.Role.SECONDARY);

        VBox primaryCard = roleCard(primaryToggle,
                "Computadora principal",
                "Servidor local",
                "Guarda los reportes, plantillas, imágenes y el historial de versiones. "
                        + "Debe permanecer encendida para compartir la información.",
                currentRole == AppConfig.Role.PRIMARY);
        VBox secondaryCard = roleCard(secondaryToggle,
                "Computadora secundaria",
                "Conexión automática",
                "Encuentra la computadora principal en la red y envía allí los reportes. "
                        + "Si no hay conexión, conserva los pendientes para sincronizarlos.",
                currentRole == AppConfig.Role.SECONDARY);

        Runnable refreshSelection = () -> {
            primaryCard.setStyle(primaryToggle.isSelected() ? SELECTED_STYLE : NORMAL_STYLE);
            secondaryCard.setStyle(secondaryToggle.isSelected() ? SELECTED_STYLE : NORMAL_STYLE);
        };
        primaryToggle.selectedProperty().addListener((obs, old, value) -> refreshSelection.run());
        secondaryToggle.selectedProperty().addListener((obs, old, value) -> refreshSelection.run());
        primaryCard.setOnMouseClicked(event -> primaryToggle.setSelected(true));
        secondaryCard.setOnMouseClicked(event -> secondaryToggle.setSelected(true));

        if (currentRole == AppConfig.Role.SECONDARY) secondaryToggle.setSelected(true);
        else primaryToggle.setSelected(true);
        refreshSelection.run();

        Label note = new Label("Solo debe existir una computadora principal por red. "
                + "Cambiar esta opción no elimina reportes ni plantillas.");
        note.setWrapText(true);
        note.setTextFill(Color.web("#475569"));
        note.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 8px; -fx-padding: 10px;");
        note.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(8, eyebrow, title, subtitle, new Region(),
                primaryCard, secondaryCard, note);
        content.setPadding(new Insets(8, 4, 2, 4));
        VBox.setMargin(primaryCard, new Insets(8, 0, 0, 0));
        VBox.setMargin(note, new Insets(6, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(570);
        dialog.getDialogPane().setStyle("-fx-background-color: #f8fafc; -fx-padding: 18px;");

        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirm);
        confirmButton.setStyle("-fx-background-color: #4f46e5; -fx-text-fill: white; "
                + "-fx-font-weight: bold; -fx-background-radius: 7px; -fx-padding: 8px 18px;");

        dialog.setResultConverter(button -> {
            if (button != confirm || roles.getSelectedToggle() == null) return null;
            return (AppConfig.Role) roles.getSelectedToggle().getUserData();
        });
        return dialog.showAndWait();
    }

    private static VBox roleCard(RadioButton toggle, String title, String badge,
                                 String description, boolean current) {
        Circle indicator = new Circle(5, Color.web("#6366f1"));
        Label titleLabel = new Label(title);
        titleLabel.setTextFill(Color.web("#1e293b"));
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        Label badgeLabel = new Label(badge);
        badgeLabel.setTextFill(Color.web("#4338ca"));
        badgeLabel.setStyle("-fx-background-color: #e0e7ff; -fx-background-radius: 10px; "
                + "-fx-padding: 3px 8px; -fx-font-size: 10px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(8, indicator, titleLabel, spacer, badgeLabel, toggle);
        heading.setAlignment(Pos.CENTER_LEFT);

        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.setTextFill(Color.web("#64748b"));
        descriptionLabel.setStyle("-fx-line-spacing: 2px;");

        VBox card = new VBox(7, heading, descriptionLabel);
        card.setPadding(new Insets(13));
        if (current) {
            Label currentLabel = new Label("Configuración actual");
            currentLabel.setTextFill(Color.web("#047857"));
            currentLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
            card.getChildren().add(currentLabel);
        }
        return card;
    }
}
