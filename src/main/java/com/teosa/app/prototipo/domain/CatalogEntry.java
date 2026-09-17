package com.teosa.app.prototipo.domain;
import java.util.*;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
public final class CatalogEntry {
 public enum Kind { CLIENT, SCOPE }
 private Kind kind; private String text,code,storedId;
 public CatalogEntry(Kind kind,String text){this.kind=kind;this.text=text;}
 public Kind getKind(){return kind;} public String getText(){return text==null?"":text.trim();}
 public String getCode(){return code==null?"":code;}
 public static String normalize(String text){return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+"," ");}
 public void setCode(String value){code=value;}
 public void retainId(String value){storedId=value;}
 public String id(){if(storedId!=null)return storedId;return UUID.nameUUIDFromBytes((kind+":"+normalize(getText())).getBytes(StandardCharsets.UTF_8)).toString();}
 public void validate(){if(storedId!=null&&!storedId.matches("[a-fA-F0-9-]{36}"))throw new IllegalArgumentException("Identificador de catálogo inválido.");if(kind==null||getText().isBlank()||getText().length()>4000)throw new IllegalArgumentException("Captura un nombre o alcance válido (máximo 4000 caracteres).");}
 public static List<CatalogEntry> matches(List<CatalogEntry> entries,Kind kind,String query){
  String q=normalize(query);if(q.isBlank())return List.of();
  return entries.stream().filter(e->e.kind==kind).filter(e->Arrays.stream(q.split(" ")).allMatch(t->normalize(e.getText()+" "+e.getCode()).contains(t)))
   .sorted(Comparator.<CatalogEntry>comparingInt(e->normalize(e.getText()).equals(q)?0:normalize(e.getText()).startsWith(q)?1:2).thenComparing(e->normalize(e.getText())))
   .limit(4).toList();
 }
}
