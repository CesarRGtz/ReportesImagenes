package com.teosa.app.prototipo.presentation;
import com.teosa.app.prototipo.App;

import java.io.IOException;
import javafx.fxml.FXML;

public class SecondaryController {

    @FXML
    private void switchToPrimary() throws IOException {
        App.setRoot("primary");
    }
}
