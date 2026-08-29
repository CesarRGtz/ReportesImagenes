module com.teosa.app.prototipo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires com.github.librepdf.openpdf;

    opens com.teosa.app.prototipo to javafx.fxml;
    exports com.teosa.app.prototipo;
}