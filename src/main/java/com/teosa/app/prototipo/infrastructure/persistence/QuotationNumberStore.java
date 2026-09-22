package com.teosa.app.prototipo.infrastructure.persistence;

import com.teosa.app.prototipo.domain.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Atomic ledger: retries and later versions retain the same number, even after deletion. */
public final class QuotationNumberStore {
    private final Path file;
    private static final java.util.concurrent.ConcurrentHashMap<Path,Object> LOCKS=new java.util.concurrent.ConcurrentHashMap<>();
    @FunctionalInterface private interface Operation<T>{T run()throws IOException;}
    private <T> T locked(Operation<T> operation)throws IOException{
        synchronized(LOCKS.computeIfAbsent(file,key->new Object())){
            Files.createDirectories(file.getParent());
            try(var channel=java.nio.channels.FileChannel.open(file.resolveSibling(file.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()){
                return operation.run();
            }
        }
    }
    private static final Pattern NUMBER=Pattern.compile("TEO(\\d{2})-(\\d{3,})");
    private static final class Ledger {Map<Integer,Integer> last=new HashMap<>();Map<String,String> assigned=new HashMap<>();}
    public QuotationNumberStore(Path file){this.file=file.toAbsolutePath().normalize();}
    private Ledger read()throws IOException{return JsonSupport.read(file,Ledger.class);}
    public synchronized void initialize(List<ReportSummary> existing)throws IOException{
        locked(()->{
        if(Files.exists(file))return null;
        Ledger ledger=new Ledger();Map<Integer,Integer> count=new HashMap<>();
        for(ReportSummary report:existing)if(report.getKind()==DocumentKind.QUOTATION){
            long registered=report.getRegisteredAt()>0?report.getRegisteredAt():report.getModifiedAt();
            int year=Instant.ofEpochMilli(registered).atZone(ZoneId.systemDefault()).getYear();count.merge(year,1,Integer::sum);
            Matcher match=NUMBER.matcher(report.getRemision()==null?"":report.getRemision());
            if(match.matches()){
                int numberYear=2000+Integer.parseInt(match.group(1));int number=Integer.parseInt(match.group(2));
                ledger.last.merge(numberYear,number,Math::max);ledger.assigned.put(report.getReportId(),report.getRemision());
            }
        }
        count.forEach((year,total)->ledger.last.merge(year,total,Math::max));JsonSupport.write(file,ledger);return null;
        });
    }
    public synchronized QuotationFolio number(String id,int year,boolean allocate)throws IOException{
        return locked(()->{
        Ledger ledger=read();String existing=ledger.assigned.get(id);
        if(existing!=null)return new QuotationFolio(existing,true);
        int next=Math.addExact(ledger.last.getOrDefault(year,0),1);
        String value=String.format(Locale.ROOT,"TEO%02d-%03d",year%100,next);
        if(allocate){ledger.last.put(year,next);ledger.assigned.put(id,value);JsonSupport.write(file,ledger);}
        return new QuotationFolio(value,allocate);
        });
    }
}
