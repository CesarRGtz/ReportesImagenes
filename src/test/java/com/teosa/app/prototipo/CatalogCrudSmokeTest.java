package com.teosa.app.prototipo;
import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import com.teosa.app.prototipo.infrastructure.network.*;
import java.nio.file.*;
import java.util.*;

public class CatalogCrudSmokeTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("teosa-crud-");System.setProperty("teosa.data.dir",root.toString());
  ServerStorage storage=new ServerStorage(root.resolve("server"));CatalogStore cache=new CatalogStore(()->root.resolve("client"));
  CatalogEntry entry=new CatalogEntry(CatalogEntry.Kind.SCOPE,"Alcance original");cache.save(entry);
  try(LocalReportServer server=new LocalReportServer(0,storage)){
   server.start();HttpReportClient client=new HttpReportClient("http://127.0.0.1:"+server.getPort());cache.synchronize(client);
   CatalogEntry edited=new CatalogEntry(CatalogEntry.Kind.SCOPE,"Alcance corregido");client.updateCatalogEntry(entry.id(),edited);cache.synchronize(client);
   check(cache.list().stream().anyMatch(e->e.id().equals(entry.id())&&e.getText().equals("Alcance corregido")),"Stable identity and update propagation");
   CatalogEntry duplicate=new CatalogEntry(CatalogEntry.Kind.SCOPE,"Otro alcance");client.saveCatalogEntry(duplicate);
   try{client.updateCatalogEntry(entry.id(),duplicate);throw new AssertionError("Accepted duplicate rename");}catch(java.io.IOException expected){}
   client.deleteCatalogEntry(entry.id());cache.synchronize(client);
   check(cache.list().stream().noneMatch(e->e.id().equals(entry.id())),"Delete propagated to cached suggestions");
   client.saveCatalogEntry(entry);check(client.listCatalog().stream().noneMatch(e->e.id().equals(entry.id())),"Stale upload resurrected deleted entry");
   CatalogEntry recreated=new CatalogEntry(CatalogEntry.Kind.SCOPE,"Alcance original");recreated.retainId(UUID.randomUUID().toString());client.saveCatalogEntry(recreated);
   check(client.listCatalog().stream().anyMatch(e->e.id().equals(recreated.id())),"Explicit new entry allowed after deletion");
   CatalogEntry seed=client.listCatalog().stream().filter(e->e.getKind()==CatalogEntry.Kind.CLIENT).findFirst().orElseThrow();client.deleteCatalogEntry(seed.id());
   CatalogStore fresh=new CatalogStore(()->root.resolve("new-computer"));fresh.synchronize(client);check(fresh.list().stream().noneMatch(e->e.id().equals(seed.id())),"Fresh installation reintroduced deleted Excel seed");
  }
  Quotation quotation=new Quotation();quotation.setValue("cliente","Cliente desconocido");QuotationLine line=new QuotationLine();line.setScope("Alcance repetido\nalcance REPETIDO\nOtro nuevo");quotation.getLines().add(line);
  check(NewCatalogData.find(quotation,List.of()).size()==3,"New data must be deduplicated");
  CatalogEntry known=new CatalogEntry(CatalogEntry.Kind.SCOPE,"Alcance repetido");known.retainId(UUID.randomUUID().toString());
  check(NewCatalogData.find(quotation,List.of(known)).size()==2,"Match names independently of stable IDs");
  check(line.getScope().contains("Alcance repetido"),"Catalog changes must not mutate existing documents");
  System.out.println("CATALOG_CRUD_PROPAGATION_AND_NEW_DATA_OK");
 }
}
