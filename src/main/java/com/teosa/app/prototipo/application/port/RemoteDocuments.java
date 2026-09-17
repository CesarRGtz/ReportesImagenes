package com.teosa.app.prototipo.application.port;
import com.teosa.app.prototipo.domain.*;
import java.io.IOException;
import java.util.List;
public interface RemoteDocuments {
 default List<CatalogEntry> listCatalog() throws IOException {throw new IOException("Actualiza el servidor para compartir catálogos.");}
 default void saveCatalogEntry(CatalogEntry entry) throws IOException {throw new IOException("Actualiza el servidor para compartir catálogos.");}
 default void updateCatalogEntry(String id,CatalogEntry entry) throws IOException {throw new IOException("Actualiza el servidor para editar catálogos.");}
 default void deleteCatalogEntry(String id) throws IOException {throw new IOException("Actualiza el servidor para eliminar datos.");}
 boolean health(); String getBaseUrl(); void setBaseUrl(String url);
 SaveResponse saveReport(ReportTransfer transfer) throws IOException;
 List<ReportSummary> listReports(String query) throws IOException;
 List<VersionSummary> listVersions(String id) throws IOException;
 ReportTransfer loadReport(String id,int version) throws IOException;
 void deleteReport(String id) throws IOException;
 void deleteVersion(String id,int version) throws IOException;
 List<TemplateDefinition> listTemplates() throws IOException;
 void saveTemplate(TemplateDefinition template) throws IOException;
 void deleteTemplate(String name) throws IOException;
}
