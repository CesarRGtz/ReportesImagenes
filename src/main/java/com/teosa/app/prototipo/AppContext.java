package com.teosa.app.prototipo;
import com.teosa.app.prototipo.application.AppServices;
import com.teosa.app.prototipo.infrastructure.network.*;
import com.teosa.app.prototipo.infrastructure.persistence.*;
/** Composition root: concrete adapters are assembled only here. */
public final class AppContext {
 private static final AppServices SERVICES=new AppServices(new OfflineQueue(),new AssetCodecAdapter(),new ConnectionRuntimeAdapter(),new LocalTemplateCache(),new CatalogStore(()->AppDirectories.base().resolve("catalogos-locales")));
 private AppContext(){}
 public static AppServices services(){return SERVICES;}
}
