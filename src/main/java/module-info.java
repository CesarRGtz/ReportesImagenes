module com.teosa.app.prototipo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires com.github.librepdf.openpdf;
    requires com.google.gson;
    requires jdk.httpserver;
    requires java.net.http;

    opens com.teosa.app.prototipo to javafx.fxml;
    opens com.teosa.app.prototipo.data to com.google.gson;
    opens com.teosa.app.prototipo.network to com.google.gson;
    exports com.teosa.app.prototipo;
}
