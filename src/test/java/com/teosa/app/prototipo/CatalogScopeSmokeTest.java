package com.teosa.app.prototipo;
import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import com.teosa.app.prototipo.infrastructure.network.*;
import com.teosa.app.prototipo.infrastructure.pdf.QuotationPdfGenerator;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;

public final class CatalogScopeSmokeTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  Path temp=Files.createTempDirectory("teosa-catalog-test-");System.setProperty("teosa.data.dir",temp.toString());
  CatalogStore first=new CatalogStore(()->temp.resolve("first")),second=new CatalogStore(()->temp.resolve("second"));
  check(first.list().size()==199,"Excel client count");
  CatalogEntry scope=new CatalogEntry(CatalogEntry.Kind.SCOPE,"Revisión del aislamiento del motor");
  CatalogEntry client=new CatalogEntry(CatalogEntry.Kind.CLIENT,"Cliente nuevo de prueba");
  first.save(scope);first.save(client);check(first.pendingCount()==2,"Durable queue");
  try{first.synchronize(new HttpReportClient(""));throw new AssertionError("Disconnected upload should fail");}catch(java.io.IOException expected){}
  first=new CatalogStore(()->temp.resolve("first"));check(first.pendingCount()==2,"Queue survives restart");
  ServerStorage storage=new ServerStorage(temp.resolve("server"));
  try(LocalReportServer server=new LocalReportServer(0,storage)){
   server.start();HttpReportClient remote=new HttpReportClient("http://127.0.0.1:"+server.getPort());
   first.synchronize(remote);first.synchronize(remote);check(first.pendingCount()==0,"Acknowledged queue");
   second.synchronize(remote);check(second.list().stream().anyMatch(e->e.id().equals(scope.id())),"Shared scope on second computer");
   second.save(new CatalogEntry(CatalogEntry.Kind.CLIENT," CLIENTE NUEVO DE PRUEBA "));second.synchronize(remote);
   check(remote.listCatalog().stream().filter(e->e.id().equals(client.id())).count()==1,"Retry/case-insensitive deduplication");
   check(new ServerStorage(temp.resolve("server")).listCatalog().size()==201,"Server persistence");
  }
  List<CatalogEntry> candidates=new ArrayList<>();for(int i=0;i<8;i++)candidates.add(new CatalogEntry(CatalogEntry.Kind.SCOPE,"Revisión motor "+i));
  candidates.add(new CatalogEntry(CatalogEntry.Kind.SCOPE,"Motor revision"));
  check(CatalogEntry.matches(candidates,CatalogEntry.Kind.SCOPE,"revision").size()==4,"Max four accent-insensitive suggestions");
  check(CatalogEntry.matches(candidates,CatalogEntry.Kind.SCOPE,"revision motor 7").getFirst().getText().endsWith("7"),"Multiword ranking");
  check(CatalogEntry.matches(candidates,CatalogEntry.Kind.CLIENT,"revision").isEmpty(),"Catalog isolation");
  QuotationLine legacy=JsonSupport.GSON.fromJson("{\"scope\":\"Primer alcance\\nSegundo alcance\",\"quantity\":2,\"unitPrice\":500}",QuotationLine.class);
  check(legacy.getScopes().size()==2 && legacy.amount().compareTo(new BigDecimal("1000"))==0,"Legacy quotation compatibility");
  Quotation q=new Quotation();q.setValue("cliente","COBRE DEL MAYO");q.setValue("folio","CAT-001");
  QuotationLine line=new QuotationLine();line.setCode("P01");line.setDescription("Servicio de mantenimiento del motor");line.setQuantity(new BigDecimal("2"));line.setUnitPrice(new BigDecimal("999"));line.setScopeBreakdown(true);
  QuotationScope a=new QuotationScope("Limpieza y revisión del motor"),b=new QuotationScope("Cambio de rodamientos y pruebas de funcionamiento con verificación de aislamiento");a.setCost(new BigDecimal("120.50"));b.setCost(new BigDecimal("79.50"));line.getScopes().addAll(List.of(a,b));q.getLines().add(line);
  check(line.getUnitPrice().compareTo(new BigDecimal("200"))==0 && q.total().compareTo(new BigDecimal("464"))==0,"Scopes determine price, quantity, tax and total");q.validate();
  Quotation copy=JsonSupport.GSON.fromJson(JsonSupport.GSON.toJson(q),Quotation.class);check(copy.total().compareTo(q.total())==0,"Breakdown JSON round trip");
  line.setScopeBreakdown(false);check(line.getUnitPrice().compareTo(new BigDecimal("200"))==0,"Disabling preserves computed price");line.setScopeBreakdown(true);
  Path out=Path.of("work/verification");Files.createDirectories(out);Path pdf=out.resolve("cotizacion-alcances.pdf");QuotationPdfGenerator.generate(pdf,q,TemplateDefinition.quotationDefaults());
  try(var document=Loader.loadPDF(pdf.toFile())){
   String text=new PDFTextStripper().getText(document);check(text.contains("A01")&&text.contains("A02")&&text.contains("120.50")&&text.contains("79.50")&&text.contains("464.00"),"Numbered PDF and consistent totals");
   ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0,120),"png",out.resolve("cotizacion-alcances.png").toFile());
  }
  line.getScopes().remove(a);check(line.getUnitPrice().compareTo(new BigDecimal("79.50"))==0,"Removing scope recalculates price");
  System.out.println("CATALOG_OFFLINE_SYNC_SCOPES_PDF_OK");
 }
}
