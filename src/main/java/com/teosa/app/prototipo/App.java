package com.teosa.app.prototipo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;

import com.teosa.app.prototipo.data.AppConfig;
import com.teosa.app.prototipo.data.ConfigStore;
import com.teosa.app.prototipo.network.AppServices;

import java.io.IOException;

/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        AppConfig config = ConfigStore.load();
        if (config.getRole() == null) {
            config.setRole(RoleConfigurationDialog.show(stage, null, true)
                    .orElse(AppConfig.Role.SECONDARY));
            ConfigStore.save(config);
        }
        try {
            AppServices.get().initialize(config);
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No fue posible iniciar los servicios: "
                    + ex.getMessage(), ButtonType.OK);
            applyTheme(alert.getDialogPane());
            alert.setHeaderText("Problema de conexión");
            alert.showAndWait();
        }
        Rectangle2D area = Screen.getPrimary().getVisualBounds();
        double initialWidth = Math.max(640, Math.min(1400, area.getWidth() - 40));
        double initialHeight = Math.max(500, Math.min(850, area.getHeight() - 40));
        scene = new Scene(loadFXML("primary"), initialWidth, initialHeight);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }

    static void applyTheme(DialogPane pane) {
        var stylesheet = App.class.getResource("app.css");
        if (stylesheet != null && !pane.getStylesheets().contains(stylesheet.toExternalForm())) {
            pane.getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    @Override
    public void stop() {
        AppServices.get().close();
    }

}
