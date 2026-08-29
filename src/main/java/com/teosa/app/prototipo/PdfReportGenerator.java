package com.teosa.app.prototipo;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class PdfReportGenerator {

    private static final Color GRIS_ETIQUETA = new Color(217, 217, 217);
    private static final Color GRIS_SECCION = new Color(191, 191, 191);
    private static final Color AZUL_TITULO = new Color(91, 118, 153);
    private static final Color AZUL_CAPTION = new Color(31, 78, 121);
    private static final Color GRIS_LINEA = new Color(140, 163, 191);

    public static void generar(File destino, ReporteServicio reporte) throws Exception {
        Document documento = new Document(PageSize.LETTER, 50, 50, 45, 45);
        PdfWriter.getInstance(documento, new FileOutputStream(destino));
        documento.open();

        Font fuenteTitulo = new Font(Font.HELVETICA, 14, Font.BOLDITALIC, AZUL_TITULO);
        Font fuenteEtiqueta = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
        Font fuenteValor = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
        Font fuenteSeccion = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
        Font fuenteNormal = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.BLACK);
        Font fuenteCategoria = new Font(Font.HELVETICA, 14, Font.BOLD, AZUL_CAPTION);
        Font fuenteDescripcionFoto = new Font(Font.HELVETICA, 11, Font.ITALIC, Color.DARK_GRAY);

        agregarEncabezado(documento, reporte, fuenteTitulo);

        PdfPTable datosTable = new PdfPTable(2);
        datosTable.setWidthPercentage(100);
        datosTable.setWidths(new float[]{38f, 62f});
        agregarFilaDatos(datosTable, "Fecha de Recepción de Equipo:", reporte.getFecha(),
                fuenteEtiqueta, fuenteValor);
        agregarFilaDatos(datosTable, "Área:", reporte.getArea(), fuenteEtiqueta, fuenteValor);
        agregarFilaDatos(datosTable, "Remisión:", reporte.getRemision(), fuenteEtiqueta, fuenteValor);
        agregarFilaDatos(datosTable, "Cotización:", reporte.getCotizacion(), fuenteEtiqueta, fuenteValor);
        agregarFilaDatos(datosTable, "Factura:", reporte.getFactura(), fuenteEtiqueta, fuenteValor);
        datosTable.setSpacingAfter(10f);
        documento.add(datosTable);

        PdfPTable tablaReporte = crearTablaReporte();
        agregarSeccion(tablaReporte, "1.  DATOS DEL EQUIPO:",
                reporte.getDatosEquipo(), fuenteSeccion, fuenteNormal);
        agregarSeccion(tablaReporte, "2.  DESCRIPCIÓN DEL TRABAJO:",
                reporte.getDescripcion(), fuenteSeccion, fuenteNormal);

        List<CategoriaFotografica> categorias = obtenerCategoriasConFotos(reporte);
        if (!categorias.isEmpty()) {
            double espacioDisponible = ReportLayout.initialPhotoSpace(
                    reporte.getDatosEquipo(), reporte.getDescripcion());
            if (ReportLayout.PHOTO_SECTION_HEIGHT > espacioDisponible) {
                documento.add(tablaReporte);
                documento.newPage();
                tablaReporte = crearTablaReporte();
                espacioDisponible = ReportLayout.CONTENT_HEIGHT;
            }
            agregarEncabezadoFotografico(tablaReporte, fuenteSeccion, false);
            espacioDisponible -= ReportLayout.PHOTO_SECTION_HEIGHT;

            for (CategoriaFotografica categoria : categorias) {
                String textoCategoria = valorOVacio(categoria.getTitulo());
                int primerFin = calcularFinFila(categoria, 0);
                float[] primerosAnchos = calcularAnchosFila(categoria, 0, primerFin);
                double altoMinimoCategoria = ReportLayout.estimateCategoryTitleHeight(textoCategoria)
                        + estimarAltoFilaPdf(categoria, 0, primerFin, primerosAnchos)
                        + ReportLayout.PHOTO_SPACING;
                if (altoMinimoCategoria > espacioDisponible) {
                    documento.add(tablaReporte);
                    documento.newPage();
                    tablaReporte = crearTablaReporte();
                    agregarEncabezadoFotografico(tablaReporte, fuenteSeccion, true);
                    espacioDisponible = ReportLayout.CONTENT_HEIGHT
                            - ReportLayout.PHOTO_SECTION_HEIGHT;
                }
                agregarTituloCategoria(tablaReporte, textoCategoria, fuenteCategoria);
                espacioDisponible -= ReportLayout.estimateCategoryTitleHeight(textoCategoria);

                int inicio = 0;
                while (inicio < categoria.getFotografias().size()) {
                    int fin = calcularFinFila(categoria, inicio);
                    float[] anchos = calcularAnchosFila(categoria, inicio, fin);
                    double altoFila = estimarAltoFilaPdf(categoria, inicio, fin, anchos)
                            + ReportLayout.PHOTO_SPACING;
                    if (altoFila > espacioDisponible) {
                        documento.add(tablaReporte);
                        documento.newPage();
                        tablaReporte = crearTablaReporte();
                        agregarEncabezadoFotografico(tablaReporte, fuenteSeccion, true);
                        String continuacion = textoCategoria + " (continuación)";
                        agregarTituloCategoria(tablaReporte, continuacion, fuenteCategoria);
                        espacioDisponible = ReportLayout.CONTENT_HEIGHT
                                - ReportLayout.PHOTO_SECTION_HEIGHT
                                - ReportLayout.estimateCategoryTitleHeight(continuacion);
                    }

                    agregarFilaFotos(tablaReporte, categoria, inicio, fin, anchos,
                            fuenteDescripcionFoto);
                    espacioDisponible -= altoFila;
                    inicio = fin;
                }
            }
        }

        documento.add(tablaReporte);
        documento.close();
    }

    private static PdfPTable crearTablaReporte() {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        tabla.setSplitRows(false);
        tabla.setSplitLate(true);
        return tabla;
    }

    private static void agregarEncabezadoFotografico(
            PdfPTable tabla, Font fuente, boolean continuacion) {
        String texto = continuacion
                ? "3.  REPORTE FOTOGRÁFICO (CONTINUACIÓN):"
                : "3.  REPORTE FOTOGRÁFICO DEL ANTES, DURANTE Y DESPUÉS DE REALIZAR EL TRABAJO:";
        PdfPCell encabezado = celdaEncabezado(texto, fuente);
        encabezado.setMinimumHeight((float) ReportLayout.PHOTO_SECTION_HEIGHT);
        tabla.addCell(encabezado);
    }

    private static void agregarTituloCategoria(
            PdfPTable tabla, String titulo, Font fuenteCategoria) {
        PdfPCell celda = new PdfPCell(new Phrase(titulo, fuenteCategoria));
        celda.setPadding(8f);
        celda.setMinimumHeight((float) ReportLayout.estimateCategoryTitleHeight(titulo));
        aplicarBorde(celda);
        tabla.addCell(celda);
    }

    private static double estimarAltoFilaPdf(
            CategoriaFotografica categoria, int inicio, int fin, float[] anchos)
            throws Exception {
        double alto = 0;
        for (int indice = inicio; indice < fin; indice++) {
            FotoEvidencia foto = categoria.getFotografias().get(indice);
            double ancho = anchos[indice - inicio];
            Image imagen = Image.getInstance(foto.getRuta());
            double altoDescripcion = ReportLayout.estimateDescriptionHeight(
                    foto.getEtiqueta(), ancho);
            double[] tamano = ReportLayout.scaleImage(
                    imagen.getWidth(), imagen.getHeight(), foto.getAncho(), ancho,
                    ReportLayout.CONTENT_HEIGHT - altoDescripcion
                            - ReportLayout.PHOTO_SPACING);
            alto = Math.max(alto, tamano[1] + altoDescripcion
                    + (ReportLayout.PHOTO_CELL_PADDING * 2));
        }
        return alto;
    }

    private static void agregarFilaFotos(
            PdfPTable tabla, CategoriaFotografica categoria, int inicio, int fin,
            float[] anchos, Font fuenteDescripcion) throws Exception {
        float[] columnas = crearColumnasConSeparacion(anchos);
        PdfPTable fila = new PdfPTable(columnas.length);
        fila.setWidths(columnas);
        fila.setTotalWidth(sumar(columnas));
        fila.setLockedWidth(true);
        fila.setHorizontalAlignment(Element.ALIGN_LEFT);
        fila.setKeepTogether(true);

        for (int indice = inicio; indice < fin; indice++) {
            if (indice > inicio) {
                PdfPCell separacion = new PdfPCell();
                separacion.setBorder(Rectangle.NO_BORDER);
                fila.addCell(separacion);
            }
            fila.addCell(crearCeldaFoto(
                    categoria.getFotografias().get(indice),
                    anchos[indice - inicio], fuenteDescripcion));
        }

        PdfPCell contenedor = new PdfPCell(fila);
        contenedor.setPadding((float) ReportLayout.PHOTO_CELL_PADDING);
        contenedor.setMinimumHeight((float) estimarAltoFilaPdf(
                categoria, inicio, fin, anchos));
        contenedor.setBorder(Rectangle.LEFT | Rectangle.RIGHT
                | (fin == categoria.getFotografias().size()
                ? Rectangle.BOTTOM : Rectangle.NO_BORDER));
        contenedor.setBorderColor(Color.BLACK);
        contenedor.setBorderWidth(1f);
        tabla.addCell(contenedor);
    }

    private static void agregarEncabezado(
            Document documento, ReporteServicio reporte, Font fuenteTitulo) throws Exception {
        String cliente = valorOVacio(reporte.getCliente());
        String empresa = cliente.equals("—") ? "" : cliente.toUpperCase();
        PdfPTable encabezado = new PdfPTable(2);
        encabezado.setWidthPercentage(100);
        encabezado.setWidths(new float[]{31f, 69f});
        encabezado.setSpacingAfter(10f);

        Image logo = Image.getInstance(App.class.getResource("Imagen12.jpg"));
        logo.scaleToFit(135f, 87f);
        PdfPCell celdaLogo = new PdfPCell(logo, false);
        celdaLogo.setBorder(Rectangle.NO_BORDER);
        celdaLogo.setPadding(0);
        celdaLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        encabezado.addCell(celdaLogo);

        Paragraph titulo = new Paragraph(
                "Reporte de Servicio Elaborado para\n"
                        + (empresa.isEmpty() ? "____________" : empresa), fuenteTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        PdfPCell celdaTitulo = new PdfPCell(titulo);
        celdaTitulo.setBorder(Rectangle.NO_BORDER);
        celdaTitulo.setPadding(0);
        celdaTitulo.setHorizontalAlignment(Element.ALIGN_CENTER);
        celdaTitulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        encabezado.addCell(celdaTitulo);
        documento.add(encabezado);

        Paragraph linea = new Paragraph();
        linea.setLeading(1f);
        linea.add(new Chunk(new LineSeparator(
                0.5f, 100f, GRIS_LINEA, Element.ALIGN_CENTER, 0)));
        linea.setSpacingAfter(15f);
        documento.add(linea);
    }

    private static void agregarSeccion(PdfPTable tabla, String titulo, String contenido,
                                       Font fuenteTitulo, Font fuenteContenido) {
        tabla.addCell(celdaEncabezado(titulo, fuenteTitulo));
        PdfPCell cuerpo = new PdfPCell(new Phrase(valorOVacio(contenido), fuenteContenido));
        cuerpo.setPaddingTop(8f);
        cuerpo.setPaddingBottom(8f);
        cuerpo.setPaddingLeft(10f);
        cuerpo.setPaddingRight(10f);
        cuerpo.setMinimumHeight((float) ReportLayout.estimateBodyHeight(contenido));
        aplicarBorde(cuerpo);
        tabla.addCell(cuerpo);
    }

    private static PdfPCell celdaEncabezado(String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setBackgroundColor(GRIS_SECCION);
        celda.setPaddingTop(6f);
        celda.setPaddingBottom(6f);
        celda.setPaddingLeft(10f);
        celda.setPaddingRight(10f);
        celda.setMinimumHeight((float) ReportLayout.SECTION_HEADER_HEIGHT);
        aplicarBorde(celda);
        return celda;
    }

    private static int calcularFinFila(CategoriaFotografica categoria, int inicio) {
        double anchoUsado = 0;
        int fin = inicio;
        while (fin < categoria.getFotografias().size()) {
            double ancho = ReportLayout.photoCellWidth(
                    categoria.getFotografias().get(fin).getAncho());
            double separacion = fin > inicio ? ReportLayout.PHOTO_GAP : 0;
            if (fin > inicio && anchoUsado + separacion + ancho > ReportLayout.MAX_PHOTO_WIDTH) {
                break;
            }
            anchoUsado += separacion + ancho;
            fin++;
        }
        return fin;
    }

    private static float[] calcularAnchosFila(
            CategoriaFotografica categoria, int inicio, int fin) {
        float[] anchos = new float[fin - inicio];
        for (int indice = inicio; indice < fin; indice++) {
            anchos[indice - inicio] = (float) ReportLayout.photoCellWidth(
                    categoria.getFotografias().get(indice).getAncho());
        }
        return anchos;
    }

    private static float[] crearColumnasConSeparacion(float[] anchos) {
        float[] columnas = new float[(anchos.length * 2) - 1];
        for (int indice = 0; indice < anchos.length; indice++) {
            columnas[indice * 2] = anchos[indice];
            if (indice < anchos.length - 1) {
                columnas[(indice * 2) + 1] = (float) ReportLayout.PHOTO_GAP;
            }
        }
        return columnas;
    }

    private static float sumar(float[] valores) {
        float total = 0;
        for (float valor : valores) {
            total += valor;
        }
        return total;
    }

    private static PdfPCell crearCeldaFoto(
            FotoEvidencia foto, float anchoCelda, Font fuenteDescripcion) throws Exception {
        Image imagen = Image.getInstance(foto.getRuta());
        double anchoInterior = anchoCelda;
        double altoDescripcion = ReportLayout.estimateDescriptionHeight(
                foto.getEtiqueta(), anchoInterior);
        double[] tamano = ReportLayout.scaleImage(
                imagen.getWidth(), imagen.getHeight(), foto.getAncho(), anchoInterior,
                ReportLayout.CONTENT_HEIGHT - altoDescripcion
                        - (ReportLayout.PHOTO_CELL_PADDING * 2));
        imagen.scaleAbsolute((float) tamano[0], (float) tamano[1]);
        imagen.setAlignment(Element.ALIGN_LEFT);

        PdfPTable bloque = new PdfPTable(1);
        bloque.setTotalWidth(anchoCelda);
        bloque.setLockedWidth(true);
        bloque.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell celdaImagen = new PdfPCell(imagen, false);
        celdaImagen.setBorder(Rectangle.NO_BORDER);
        celdaImagen.setPadding(0f);
        celdaImagen.setHorizontalAlignment(Element.ALIGN_LEFT);
        bloque.addCell(celdaImagen);

        if (altoDescripcion > 0) {
            PdfPCell descripcion = new PdfPCell(new Phrase(
                    foto.getEtiqueta().trim(), fuenteDescripcion));
            descripcion.setBorder(Rectangle.NO_BORDER);
            descripcion.setPadding(0f);
            descripcion.setPaddingTop(6f);
            descripcion.setMinimumHeight((float) altoDescripcion);
            bloque.addCell(descripcion);
        }

        PdfPCell celda = new PdfPCell(bloque);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(0f);
        celda.setVerticalAlignment(Element.ALIGN_TOP);
        return celda;
    }

    private static void aplicarBorde(PdfPCell celda) {
        celda.setBorderColor(Color.BLACK);
        celda.setBorderWidth(1f);
    }

    private static void agregarFilaDatos(PdfPTable tabla, String etiqueta, String valor,
                                         Font fuenteEtiqueta, Font fuenteValor) {
        PdfPCell celdaEtiqueta = new PdfPCell(new Phrase(etiqueta, fuenteEtiqueta));
        celdaEtiqueta.setBackgroundColor(GRIS_ETIQUETA);
        celdaEtiqueta.setPaddingTop(6f);
        celdaEtiqueta.setPaddingBottom(6f);
        celdaEtiqueta.setPaddingLeft(10f);
        celdaEtiqueta.setPaddingRight(10f);
        celdaEtiqueta.setMinimumHeight((float) (ReportLayout.GENERAL_DATA_HEIGHT / 5.0));
        celdaEtiqueta.setBorderColor(Color.GRAY);
        celdaEtiqueta.setBorderWidth(0.5f);

        PdfPCell celdaValor = new PdfPCell(new Phrase(valorOVacio(valor), fuenteValor));
        celdaValor.setBackgroundColor(Color.WHITE);
        celdaValor.setPaddingTop(6f);
        celdaValor.setPaddingBottom(6f);
        celdaValor.setPaddingLeft(10f);
        celdaValor.setPaddingRight(10f);
        celdaValor.setMinimumHeight((float) (ReportLayout.GENERAL_DATA_HEIGHT / 5.0));
        celdaValor.setBorderColor(Color.GRAY);
        celdaValor.setBorderWidth(0.5f);
        tabla.addCell(celdaEtiqueta);
        tabla.addCell(celdaValor);
    }

    private static String valorOVacio(String texto) {
        return texto == null || texto.trim().isEmpty() ? "—" : texto.trim();
    }

    private static List<CategoriaFotografica> obtenerCategoriasConFotos(ReporteServicio reporte) {
        List<CategoriaFotografica> resultado = new ArrayList<>();
        if (reporte.getCategoriasFotograficas() != null) {
            for (CategoriaFotografica categoria : reporte.getCategoriasFotograficas()) {
                if (categoria != null && categoria.getFotografias() != null
                        && !categoria.getFotografias().isEmpty()) {
                    resultado.add(categoria);
                }
            }
        }
        if (resultado.isEmpty() && reporte.getFotografias() != null
                && !reporte.getFotografias().isEmpty()) {
            CategoriaFotografica legacy = new CategoriaFotografica("Evidencia fotográfica");
            legacy.getFotografias().addAll(reporte.getFotografias());
            resultado.add(legacy);
        }
        return resultado;
    }
}
