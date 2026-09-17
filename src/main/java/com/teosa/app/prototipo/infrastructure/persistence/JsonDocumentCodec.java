package com.teosa.app.prototipo.infrastructure.persistence;
import com.teosa.app.prototipo.application.port.DocumentCodec;
public final class JsonDocumentCodec implements DocumentCodec {
    public String toJson(Object value){return JsonSupport.GSON.toJson(value);}
    public <T> T fromJson(String json,Class<T> type){return JsonSupport.GSON.fromJson(json,type);}
}
