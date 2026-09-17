package com.teosa.app.prototipo;
import java.nio.file.*;
public final class ArchitectureSmokeTest {
    public static void main(String[] args)throws Exception {
        Path root=Path.of("src/main/java/com/teosa/app/prototipo");
        for(String layer:new String[]{"domain","application"})try(var files=Files.walk(root.resolve(layer))){
            for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()){
                String text=Files.readString(file);
                for(String forbidden:new String[]{"import javafx.","import com.google.","import com.lowagie.","import org.apache.",".infrastructure.",".presentation.","AppContext"})
                    if(text.contains(forbidden))throw new AssertionError(file+" depende de "+forbidden);
                if(layer.equals("domain")&&text.contains(".application."))throw new AssertionError("Dominio depende de aplicación");
            }
        }
        System.out.println("ARCHITECTURE_DEPENDENCIES_OK");
    }
}
