package com.teosa.app.prototipo.infrastructure.network;
import com.teosa.app.prototipo.application.port.*;
import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import java.io.IOException;
import java.time.Duration;
public final class ConnectionRuntimeAdapter implements ConnectionRuntime {
 private LocalReportServer server;
 private DiscoveryAnnouncer announcer;
 public RemoteDocuments initialize(AppConfig config) throws IOException {
  close();
  if(config.getRole()==AppConfig.Role.PRIMARY) {
   ServerStorage storage=new ServerStorage(AppDirectories.serverData());
   try { server=new LocalReportServer(config.getServerPort(),storage); }
   catch(IOException busy) { server=new LocalReportServer(0,storage); }
   server.start(); announcer=new DiscoveryAnnouncer(server.getPort()); announcer.start();
   config.setServerUrl("http://127.0.0.1:"+server.getPort()); ConfigStore.save(config);
  }
  return new HttpReportClient(config.getServerUrl());
 }
 public void reconnect(RemoteDocuments remote,AppConfig config) throws IOException {
  String found=DiscoveryClient.discover(Duration.ofSeconds(4));
  if(found!=null) {remote.setBaseUrl(found);config.setServerUrl(found);ConfigStore.save(config);}
 }
 public String user(){return UserIdentity.user();}
 public String computer(){return UserIdentity.computer();}
 public void close(){if(announcer!=null)announcer.close();if(server!=null)server.close();announcer=null;server=null;}
}
