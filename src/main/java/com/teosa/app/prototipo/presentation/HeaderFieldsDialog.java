package com.teosa.app.prototipo.presentation;

import com.teosa.app.prototipo.domain.HeaderLine;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Window;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Inline header text with a separate live style editor for each line. */
final class HeaderFieldsDialog {
    private HeaderFieldsDialog(){}
    static VBox content(List<HeaderLine> lines,Function<HeaderLine,Node> styleEditor,Runnable changed){
        VBox rows=new VBox(8);
        int[] sequence={0};
        Consumer<HeaderLine> append=line->{
            int index=sequence[0]++;
            TextField text=new TextField(line.getText());text.setId("header-text-"+index);
            text.setPromptText("Texto del encabezado");text.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(text,Priority.ALWAYS);
            text.textProperty().addListener((o,a,value)->{line.setText(value);changed.run();});
            Button edit=iconButton("Editar estilo del encabezado");edit.setId("header-style-"+index);
            edit.setOnAction(event->show(edit,line,styleEditor.apply(line)));
            Button remove=iconButton("Quitar línea");
            HBox row=new HBox(6,text,edit,remove);row.setAlignment(Pos.CENTER_LEFT);
            remove.setOnAction(event->{ViewportPosition position=ViewportPosition.captureAncestor(rows);lines.remove(line);rows.getChildren().remove(row);changed.run();position.restore();});
            rows.getChildren().add(row);
        };
        List.copyOf(lines).forEach(append);
        Button add=AppIcons.button("Agregar línea de encabezado");
        add.setOnAction(event->{ViewportPosition position=ViewportPosition.captureAncestor(rows);HeaderLine line=new HeaderLine("Nueva línea",11,false,false,"#333333");lines.add(line);append.accept(line);changed.run();position.restore();});
        return new VBox(8,rows,add);
    }
    private static Button iconButton(String text){
        Button button=AppIcons.button(text);button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setAccessibleText(text);button.setTooltip(new Tooltip(text));button.setMinWidth(34);return button;
    }
    private static void show(Node anchor,HeaderLine line,Node styleEditor){
        Window owner=anchor.getScene().getWindow();
        Dialog<Void> dialog=new Dialog<>();dialog.initOwner(owner);dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Estilo del campo de encabezado");dialog.setResizable(true);
        DialogPane pane=dialog.getDialogPane();pane.setId("header-style-dialog");
        pane.getStylesheets().addAll(anchor.getScene().getStylesheets());
        pane.getButtonTypes().add(new ButtonType("Cerrar",ButtonBar.ButtonData.CANCEL_CLOSE));
        Label selected=new Label(line.getText().isBlank()?"Campo sin texto":line.getText());selected.setWrapText(true);selected.getStyleClass().add("section-title");
        Label help=new Label("Los cambios de estilo se aplican solo a este campo y se muestran al instante en la vista previa.");help.setWrapText(true);
        pane.setContent(new VBox(14,selected,styleEditor,help));pane.setPrefWidth(420);
        dialog.setOnShown(event->{
            dialog.setX(owner.getX()+Math.max(0,owner.getWidth()-dialog.getWidth()-16));
            dialog.setY(owner.getY()+Math.max(0,(owner.getHeight()-dialog.getHeight())/2));
        });
        dialog.show();
    }
}
