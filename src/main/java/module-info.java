module com.teosa.app.prototipo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires com.github.librepdf.openpdf;
    requires com.google.gson;
    requires org.apache.pdfbox;
    requires org.apache.commons.logging;
    requires jdk.httpserver;
    requires java.net.http;

    opens com.teosa.app.prototipo.domain to com.google.gson;
    opens com.teosa.app.prototipo.presentation to javafx.fxml;
    opens com.teosa.app.prototipo.infrastructure.network to com.google.gson;
    opens com.teosa.app.prototipo.infrastructure.persistence to com.google.gson;
    exports com.teosa.app.prototipo;
}
