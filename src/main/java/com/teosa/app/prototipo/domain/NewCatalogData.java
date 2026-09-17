package com.teosa.app.prototipo.domain;
import java.util.*;
public final class NewCatalogData {
 private NewCatalogData(){}
 public static List<CatalogEntry> find(Quotation quotation,List<CatalogEntry> catalog){
  Set<String> known=new HashSet<>();for(CatalogEntry entry:catalog)known.add(key(entry));
  List<CatalogEntry> result=new ArrayList<>();
  add(new CatalogEntry(CatalogEntry.Kind.CLIENT,quotation.value("cliente")),known,result);
  for(QuotationLine line:quotation.getLines())for(QuotationScope scope:line.getScopes())add(new CatalogEntry(CatalogEntry.Kind.SCOPE,scope.getDescription()),known,result);
  return result;
 }
 private static String key(CatalogEntry entry){return entry.getKind()+":"+CatalogEntry.normalize(entry.getText());}
 private static void add(CatalogEntry entry,Set<String> known,List<CatalogEntry> result){if(!entry.getText().isBlank()&&known.add(key(entry)))result.add(entry);}
}
