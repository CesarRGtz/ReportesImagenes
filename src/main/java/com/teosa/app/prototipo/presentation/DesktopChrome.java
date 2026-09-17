package com.teosa.app.prototipo.presentation;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Shared visual chrome for the two desktop document editors. */
public final class DesktopChrome {
    private DesktopChrome() {}

    public static HBox header(String title,String subtitle) {
        Label mark=label("T","brand-mark");
        VBox brand=new VBox(-1,label(title,"brand-title"),label(subtitle,"brand-subtitle"));
        Region space=new Region();HBox.setHgrow(space,Priority.ALWAYS);
        HBox bar=new HBox(9,mark,brand,space);bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("app-topbar");return bar;
    }

    public static Label label(String text,String style) {
        Label label=new Label(text);label.getStyleClass().add(style);return label;
    }

    public static void addServerHome(Parent view,Runnable action) {
        if(!(view instanceof BorderPane pane) || !(pane.getTop() instanceof HBox header))
            throw new IllegalArgumentException("El editor necesita un encabezado de aplicación.");
        Button back=AppIcons.button("Menú principal");back.setId("return-home");
        back.getStyleClass().add("button-secondary");back.setOnAction(e->action.run());
        header.getChildren().add(back);
    }
}
