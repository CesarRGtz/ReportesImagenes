package com.teosa.app.prototipo.application.port;
import com.teosa.app.prototipo.domain.*;
import java.io.IOException;
import java.util.List;
public interface LocalDocuments {
 String enqueue(ReportTransfer transfer) throws IOException;
 int count();
 default List<QuotationFolioChange> folioChanges()throws IOException{return List.of();}
 default void acknowledgeFolioChange(String reportId,String current)throws IOException{}

 SaveResponse saveLocallyAndTryUpload(ReportTransfer transfer,RemoteDocuments remote) throws IOException;
 void flush(RemoteDocuments remote) throws IOException;
 List<ReportSummary> listReports(String query) throws IOException;
 List<VersionSummary> listVersions(String id) throws IOException;
 ReportSnapshot load(String localId) throws IOException;
 ReportSnapshot loadVersion(String id,int version) throws IOException;
 void cacheSynced(ReportTransfer transfer) throws IOException;
 void deleteLocalReport(String id) throws IOException;
 void deleteSyncedVersion(String id,int version) throws IOException;
 void deleteLocalVersion(String id) throws IOException;
}
