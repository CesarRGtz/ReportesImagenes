package com.teosa.app.prototipo.application.port;
import com.teosa.app.prototipo.domain.*;
import java.io.IOException;
public interface ConnectionRuntime extends AutoCloseable {
 RemoteDocuments initialize(AppConfig config) throws IOException;
 void reconnect(RemoteDocuments remote,AppConfig config) throws IOException;
 String user(); String computer();
 void close();
}
