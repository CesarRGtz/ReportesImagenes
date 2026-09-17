package com.teosa.app.prototipo.application.port;
public interface DocumentCodec {
    String toJson(Object value);
    <T> T fromJson(String json, Class<T> type);
    default <T> T copy(T value, Class<T> type) { return fromJson(toJson(value), type); }
}
