package com.teosa.app.prototipo.infrastructure.persistence;
import com.teosa.app.prototipo.domain.CatalogEntry;
import com.teosa.app.prototipo.application.port.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;

/** Append-only catalog with durable pending flags and deterministic IDs for retry-safe uploads. */
public final class CatalogStore implements CatalogCache {
 private final Supplier<Path> root;
 private static final class Entry {CatalogEntry value;boolean pending;boolean deleted;}
 public CatalogStore(Supplier<Path> root){this.root=root;}
 private Path path(CatalogEntry entry){return root.get().resolve(entry.id()+".json");}
 private void seed() throws IOException {
  Files.createDirectories(root.get());
  if(Files.exists(root.get().resolve("seed-v1.done")))return;
  try(var input=CatalogStore.class.getResourceAsStream("/com/teosa/app/prototipo/clients-seed.json")){
   if(input==null)throw new IOException("No se encontró el catálogo inicial de clientes.");
   CatalogEntry[] entries=JsonSupport.GSON.fromJson(new String(input.readAllBytes(),StandardCharsets.UTF_8),CatalogEntry[].class);
   for(CatalogEntry value:entries)if(!Files.exists(path(value))){Entry e=new Entry();e.value=value;JsonSupport.write(path(value),e);}
  }
  Files.writeString(root.get().resolve("seed-v1.done"),"1");
 }
 private List<Entry> entries() throws IOException {
  seed();List<Entry> result=new ArrayList<>();
  try(var files=Files.list(root.get())){for(Path path:files.filter(f->f.toString().endsWith(".json")).toList())result.add(JsonSupport.read(path,Entry.class));}return result;
 }
 public synchronized List<CatalogEntry> list()throws IOException{return entries().stream().filter(e->!e.deleted).map(e->e.value).toList();}
 public synchronized void save(CatalogEntry value)throws IOException{store(value,true);}
 public synchronized void accept(CatalogEntry value)throws IOException{store(value,false);}
 private void store(CatalogEntry value,boolean pending)throws IOException{
  value.validate();seed();
  // Normalize aliases as well as stable IDs after a rename.
  if(entries().stream().anyMatch(e->!e.deleted&&e.value.getKind()==value.getKind() && CatalogEntry.normalize(e.value.getText()).equals(CatalogEntry.normalize(value.getText()))))return;
  Path file=path(value);if(Files.exists(file))return;
  Entry entry=new Entry();entry.value=value;entry.pending=pending;JsonSupport.write(file,entry);
 }
 public synchronized int pendingCount()throws IOException{return (int)entries().stream().filter(e->e.pending).count();}
 private Path existingPath(String id)throws IOException {
  if(id==null||!id.matches("[a-fA-F0-9-]{36}"))throw new IOException("Identificador de catálogo inválido.");
  return root.get().resolve(id+".json");
 }
 public synchronized void update(String id,CatalogEntry value)throws IOException {
  value.validate();seed();Path file=existingPath(id);
  if(!Files.exists(file))throw new IOException("El dato ya no existe. Actualiza la lista.");
  Entry previous=JsonSupport.read(file,Entry.class);
  if(previous.deleted||previous.value.getKind()!=value.getKind())throw new IOException("No se puede editar este dato.");
  if(entries().stream().anyMatch(e->!e.deleted&&!e.value.id().equals(id)&&e.value.getKind()==value.getKind()&&CatalogEntry.normalize(e.value.getText()).equals(CatalogEntry.normalize(value.getText()))))throw new IOException("Ya existe un dato con ese nombre.");
  value.retainId(id);Entry entry=new Entry();entry.value=value;JsonSupport.write(file,entry);
 }
 public synchronized void delete(String id)throws IOException {
  seed();Path file=existingPath(id);if(!Files.exists(file))return;
  Entry entry=JsonSupport.read(file,Entry.class);entry.deleted=true;entry.pending=false;JsonSupport.write(file,entry);
 }
 public synchronized void synchronize(RemoteDocuments remote)throws IOException{
  List<Entry> snapshot;synchronized(this){snapshot=entries();}
  for(Entry entry:snapshot)if(entry.pending){
   remote.saveCatalogEntry(entry.value);
   synchronized(this){entry.pending=false;JsonSupport.write(path(entry.value),entry);}
  }
  List<CatalogEntry> received=remote.listCatalog();
  Set<Path> active=new HashSet<>();
  for(CatalogEntry value:received){value.validate();Path file=path(value);active.add(file);
   if(Files.exists(file)&&JsonSupport.read(file,Entry.class).pending)continue;
   Entry entry=new Entry();entry.value=value;JsonSupport.write(file,entry);
  }
  for(Entry entry:entries())if(!entry.pending&&!active.contains(path(entry.value)))Files.deleteIfExists(path(entry.value));
 }
}
