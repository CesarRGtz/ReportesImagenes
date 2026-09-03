package com.teosa.app.prototipo;

import com.teosa.app.prototipo.data.JsonSupport;

public class JsonModuleAccessSmokeTest {
    public static void main(String[] args) {
        ReporteServicio report = new ReporteServicio(
                "Cliente de prueba", "02/09/2026", "Taller", "R-1",
                "C-1", "F-1", "Motor", "Prueba de serialización");
        CategoriaFotografica category = new CategoriaFotografica("Antes");
        category.agregarFotografia(new FotoEvidencia("foto.jpg", "Detalle"));
        report.agregarCategoriaFotografica(category);

        String json = JsonSupport.GSON.toJson(report);
        ReporteServicio restored = JsonSupport.GSON.fromJson(json, ReporteServicio.class);
        if (!"Cliente de prueba".equals(restored.getCliente())
                || restored.getCategoriasFotograficas().size() != 1
                || restored.getCategoriasFotograficas().getFirst().getFotografias().size() != 1) {
            throw new AssertionError("El reporte no se reconstruyó correctamente desde JSON");
        }
        System.out.println("JSON_MODULE_ACCESS_OK");
    }
}
