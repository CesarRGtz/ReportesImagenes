package com.teosa.app.prototipo;

import com.teosa.app.prototipo.domain.*;
import com.teosa.app.prototipo.infrastructure.network.*;
import com.teosa.app.prototipo.infrastructure.persistence.JsonSupport;
import java.nio.file.*;
import java.time.*;

public class MonthlyRegistrationSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("teosa-registration-");
        System.setProperty("teosa.data.dir",root.toString());
        ServerStorage storage=new ServerStorage(root.resolve("server"));
        ReportSnapshot snapshot=new ReportSnapshot();snapshot.setReportId("registration-test");snapshot.setQuotation(new Quotation());
        ReportTransfer transfer=new ReportTransfer();transfer.setSnapshot(snapshot);
        storage.saveReport(transfer);
        long first=storage.listReports("").getFirst().getRegisteredAt();
        storage.saveReport(transfer);
        var summary=storage.listReports("").getFirst();
        if(summary.getRegisteredAt()!=first || !summary.registeredIn(YearMonth.now(),ZoneId.systemDefault()) || storage.listReports("").size()!=1)throw new AssertionError("Updates changed first registration");
        storage.deleteVersion("registration-test",1);
        if(new ServerStorage(root.resolve("server")).listReports("").getFirst().getRegisteredAt()!=first)throw new AssertionError("Deletion/restart lost registration");
        snapshot.setReportId("legacy-registration");storage.saveReport(transfer);
        Path folder=root.resolve("server/reportes/legacy-registration");
        Path version=folder.resolve("versiones/version-00001.json");
        ReportSnapshot old=JsonSupport.read(version,ReportSnapshot.class);
        long previous=YearMonth.now().minusMonths(1).atDay(10).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        old.setSavedAt(previous);JsonSupport.write(version,old);Files.delete(folder.resolve("registered-at.json"));
        storage.saveReport(transfer);
        var legacy=storage.listReports("").stream().filter(s->s.getReportId().equals("legacy-registration")).findFirst().orElseThrow();
        if(legacy.getRegisteredAt()!=previous || legacy.registeredIn(YearMonth.now(),ZoneId.systemDefault()))throw new AssertionError("Old document counted as new after edit");
        try(LocalReportServer server=new LocalReportServer(0,storage)){
            server.start();var client=new HttpReportClient("http://127.0.0.1:"+server.getPort());
            if(client.listReports("").stream().filter(s->s.registeredIn(YearMonth.now(),ZoneId.systemDefault())).count()!=1)throw new AssertionError("Registration did not survive network serialization");
        }
        ReportSummary boundary=new ReportSummary();ZoneId zone=ZoneId.of("America/Hermosillo");YearMonth month=YearMonth.of(2026,9);
        long start=month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
        boundary.setRegisteredAt(start-1);if(boundary.registeredIn(month,zone))throw new AssertionError("Previous month boundary");
        boundary.setRegisteredAt(start);if(!boundary.registeredIn(month,zone))throw new AssertionError("Month start boundary");
        boundary.setRegisteredAt(month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli());if(boundary.registeredIn(month,zone))throw new AssertionError("Next month boundary");
        System.out.println("MONTHLY_REGISTRATION_OK");
    }
}
