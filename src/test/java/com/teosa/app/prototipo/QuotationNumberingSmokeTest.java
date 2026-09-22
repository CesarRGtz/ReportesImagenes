package com.teosa.app.prototipo;
import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
import com.teosa.app.prototipo.infrastructure.network.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class QuotationNumberingSmokeTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static ReportTransfer transfer(String id){
        Quotation q=new Quotation();q.setAutomaticFolio(true);q.setValue("folio","TEO26-PEND-TEST");q.setValue("cliente","Cliente");
        QuotationLine line=new QuotationLine();line.setDescription("Servicio de prueba");q.getLines().add(line);
        ReportSnapshot snapshot=new ReportSnapshot();snapshot.setReportId(id);snapshot.setQuotation(q);snapshot.setTemplate(TemplateDefinition.quotationDefaults());
        ReportTransfer transfer=new ReportTransfer();transfer.setSnapshot(snapshot);return transfer;
    }
    public static void main(String[] args)throws Exception{
        Path temp=Files.createTempDirectory("teosa-folios-");System.setProperty("teosa.data.dir",temp.resolve("local").toString());
        String prefix=String.format("TEO%02d-",LocalDate.now().getYear()%100);
        ServerStorage storage=new ServerStorage(temp.resolve("server"));
        try(LocalReportServer server=new LocalReportServer(0,storage)){
            server.start();HttpReportClient client=new HttpReportClient("http://127.0.0.1:"+server.getPort());
            check(client.quotationFolio("document-one",false).value().equals(prefix+"001"),"First suggestion");
            check(client.quotationFolio("document-two",false).value().equals(prefix+"001"),"Preview must not consume counter");
            check(client.quotationFolio("document-one",true).value().equals(prefix+"001"),"First issue");
            check(client.quotationFolio("document-one",true).value().equals(prefix+"001"),"Retries must reuse number");
            try(var threads=Executors.newFixedThreadPool(4)){
                List<Future<String>> jobs=new ArrayList<>();for(int i=0;i<8;i++){String id="concurrent-"+i;jobs.add(threads.submit(()->client.quotationFolio(id,true).value()));}
                Set<String> numbers=new HashSet<>();for(var job:jobs)numbers.add(job.get());check(numbers.size()==8,"Concurrent duplicate numbers");
            }
            ReportTransfer first=transfer("document-one");SaveResponse v1=client.saveReport(first);
            first.getSnapshot().getQuotation().setValue("folio",prefix+"999");first.getSnapshot().getQuotation().setFolioAssigned(true);
            SaveResponse v2=client.saveReport(first);
            check(v1.getQuotationFolio().equals(v2.getQuotationFolio())&&v2.getVersion()==2,"Versions changed number");
            check(client.quotationFolio("next-document",false).value().equals(prefix+"010"),"Version increased counter");
            client.deleteReport("document-one");check(client.quotationFolio("next-document",false).value().equals(prefix+"010"),"Deleted number reused");
            OfflineQueue queue=new OfflineQueue();String pending=queue.enqueue(transfer("offline-document"));
            queue.enqueue(transfer("offline-document"));queue.flush(client);check(queue.count()==0,"Offline queue not uploaded");
            var localVersions=queue.listVersions("offline-document");check(localVersions.size()==2,"Offline versions missing");
            for(var version:localVersions){ReportSnapshot local=queue.loadVersion("offline-document",version.getVersion());check(local.getQuotation().value("folio").equals(prefix+"010")&&local.getQuotation().isFolioAssigned(),"Local copy has provisional number after sync");}
            check(new OfflineQueue().folioChanges().size()==1,"Folio correction notification must survive restart and merge versions");
            var notice=queue.folioChanges().getFirst();check(notice.previous().contains("PEND")&&notice.current().equals(prefix+"010"),"Incorrect correction notice");
            queue.acknowledgeFolioChange(notice.reportId(),notice.current());check(queue.folioChanges().isEmpty(),"Correction acknowledgement not persisted");
            check(client.quotationFolio("next-document",false).value().equals(prefix+"011"),"Offline versions consumed extra numbers");
        }
        ServerStorage restarted=new ServerStorage(temp.resolve("server"));check(restarted.quotationFolio("offline-document",true).value().equals(prefix+"010"),"Restart lost assigned folio");
        QuotationNumberStore annual=new QuotationNumberStore(temp.resolve("annual.json"));annual.initialize(List.of());
        check(annual.number("annual-1",2026,true).value().equals("TEO26-001"),"2026 format");
        check(annual.number("annual-2",2027,true).value().equals("TEO27-001"),"Annual reset");
        check(annual.number("annual-1",2027,true).value().equals("TEO26-001"),"Old quotation renumbered in new year");
        for(int i=0;i<999;i++)annual.number("many-"+i,2027,true);
        check(annual.number("after-thousand",2027,true).value().equals("TEO27-1001"),"Counter truncated after 999");
        Path sharedFile=temp.resolve("shared-process-ledger.json");
        QuotationNumberStore one=new QuotationNumberStore(sharedFile),two=new QuotationNumberStore(sharedFile);one.initialize(List.of());two.initialize(List.of());
        try(var workers=Executors.newFixedThreadPool(8)){
            List<Future<String>> requests=new ArrayList<>();for(int i=0;i<20;i++){int n=i;requests.add(workers.submit(()->(n%2==0?one:two).number("shared-"+n,2026,true).value()));}
            Set<String> values=new HashSet<>();for(var result:requests)values.add(result.get());check(values.size()==20,"Two storage instances reused a number");
        }
        System.out.println("ANNUAL_FOLIOS_CONCURRENCY_RESTART_OFFLINE_OK");
    }
}
