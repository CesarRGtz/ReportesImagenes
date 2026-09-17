package com.teosa.app.prototipo.application.port;
import com.teosa.app.prototipo.domain.CatalogEntry;
import java.io.IOException;
import java.util.List;
public interface CatalogCache {
 List<CatalogEntry> list() throws IOException;
 void save(CatalogEntry entry) throws IOException;
 void synchronize(RemoteDocuments remote) throws IOException;
 int pendingCount() throws IOException;
}
