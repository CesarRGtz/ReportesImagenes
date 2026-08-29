package com.teosa.app.prototipo;

import java.util.List;

final class PhotoOrder {

    private PhotoOrder() {
    }

    static boolean move(List<CategoriaFotografica> categorias,
                        int categoriaOrigen, int fotoOrigen,
                        int categoriaDestino, int indiceInsercion) {
        if (categoriaOrigen < 0 || categoriaOrigen >= categorias.size()
                || categoriaDestino < 0 || categoriaDestino >= categorias.size()) {
            return false;
        }

        List<FotoEvidencia> origen = categorias.get(categoriaOrigen).getFotografias();
        List<FotoEvidencia> destino = categorias.get(categoriaDestino).getFotografias();
        if (fotoOrigen < 0 || fotoOrigen >= origen.size()) {
            return false;
        }

        FotoEvidencia foto = origen.remove(fotoOrigen);
        if (categoriaOrigen == categoriaDestino && fotoOrigen < indiceInsercion) {
            indiceInsercion--;
        }
        indiceInsercion = Math.max(0, Math.min(indiceInsercion, destino.size()));
        destino.add(indiceInsercion, foto);
        return true;
    }
}
