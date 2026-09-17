package com.teosa.app.prototipo.infrastructure.persistence;
import com.teosa.app.prototipo.application.port.*;
import com.teosa.app.prototipo.domain.TemplateDefinition;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Durable template changes. Pending edits win over server cache until acknowledged. */
public final class LocalTemplateCache implements TemplateCache {
    private Path root(){return AppDirectories.base().resolve("plantillas-locales");}
    private Path path(String key){return root().resolve(UUID.nameUUIDFromBytes(key.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))+".json");}
    private static final class Entry {String name;TemplateDefinition template;boolean pending;boolean deleted;}
    private List<Entry> entries()throws IOException {
        if(!Files.exists(root()))return List.of();List<Entry> result=new ArrayList<>();
        try(var files=Files.list(root())){for(Path file:files.filter(p->p.toString().endsWith(".json")).toList())result.add(JsonSupport.read(file,Entry.class));}
        return result;
    }
    public synchronized List<TemplateDefinition> list()throws IOException {
        return entries().stream().filter(e->!e.deleted).map(e->e.template).sorted(Comparator.comparingLong(TemplateDefinition::getLastUsedAt).reversed()).toList();
    }
    public synchronized void save(TemplateDefinition template)throws IOException {
        if(template.getName().isBlank())throw new IOException("La plantilla necesita un nombre.");
        Entry e=new Entry();e.name=template.storageName();e.template=template;e.pending=true;JsonSupport.write(path(e.name),e);
    }
    public synchronized void delete(String name)throws IOException {
        Entry e=new Entry();e.name=name;e.pending=true;e.deleted=true;JsonSupport.write(path(name),e);
    }
    public synchronized void synchronize(RemoteDocuments remote)throws IOException {
        for(Entry e:entries())if(e.pending){
            if(e.deleted){remote.deleteTemplate(e.name);Files.deleteIfExists(path(e.name));}
            else{remote.saveTemplate(e.template);e.pending=false;JsonSupport.write(path(e.name),e);}
        }
        List<TemplateDefinition> templates=remote.listTemplates();Set<Path> active=new HashSet<>();
        for(TemplateDefinition t:templates){Entry e=new Entry();e.name=t.storageName();e.template=t;Path file=path(e.name);active.add(file);JsonSupport.write(file,e);}
        for(Entry e:entries())if(!e.pending&&!active.contains(path(e.name)))Files.deleteIfExists(path(e.name));
    }
}
