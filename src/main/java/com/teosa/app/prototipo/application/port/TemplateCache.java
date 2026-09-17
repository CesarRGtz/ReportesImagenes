package com.teosa.app.prototipo.application.port;
import com.teosa.app.prototipo.domain.TemplateDefinition;
import java.io.IOException;
import java.util.List;
public interface TemplateCache {
    List<TemplateDefinition> list() throws IOException;
    void save(TemplateDefinition template) throws IOException;
    void delete(String name) throws IOException;
    void synchronize(RemoteDocuments remote) throws IOException;
}
