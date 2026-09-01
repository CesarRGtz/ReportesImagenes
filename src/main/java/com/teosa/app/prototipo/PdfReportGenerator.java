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
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.teosa.app.prototipo.data.CustomFieldValue;
import com.teosa.app.prototipo.data.FieldDefinition;
import com.teosa.app.prototipo.data.HeaderLine;
import com.teosa.app.prototipo.data.TemplateDefinition;
import com.teosa.app.prototipo.data.TextStyle;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfReportGenerator {

    private static final Color GRIS_ETIQUETA = new Color(217, 217, 217);
    private static final Color GRIS_SECCION = new Color(191, 191, 191);
    private static final Color AZUL_TITULO = new Color(91, 118, 153);
    private static final Color AZUL_CAPTION = new Color(31, 78, 121);
    private static final Color GRIS_LINEA = new Color(140, 163, 191);

    public static void generar(File destino, ReporteServicio reporte) throws Exception {
        generar(destino, reporte, TemplateDefinition.defaults());
    }

    public static void generar(File destino, ReporteServicio reporte,
                               TemplateDefinition template) throws Exception {
        if (template == null) template = TemplateDefinition.defaults();
        Document documento = new Document(PageSize.LETTER, 50, 50, 45, 45);
        PdfWriter.getInstance(documento, new FileOutputStream(destino));
        documento.open();

        Font fuenteEtiqueta = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
        Font fuenteValor = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
        Font fuenteSeccion = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
        Font fuenteNormal = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.BLACK);
        Font fuenteCategoria = crearFuente(template.getCategoryTitleStyle());
        Font fuenteDescripcionFoto = crearFuente(template.getPhotoCommentStyle());

        agregarEncabezado(documento, reporte, template);

        PdfPTable datosTable = new PdfPTable(2);
        datosTable.setWidthPercentage(100);
        datosTable.setWidths(new float[]{38f, 62f});
        List<FieldDefinition> camposVisibles = template.orderedFields().stream()
                .filter(FieldDefinition::isVisible).toList();
        Map<String, String> valores = valoresCampos(reporte);
        for (FieldDefinition campo : camposVisibles) {
            agregarFilaDatos(datosTable, campo.getLabel(), valores.get(campo.getKey()),
                    fuenteEtiqueta, fuenteValor, color(campo.getBackgroundColor(), GRIS_ETIQUETA),
                    Math.max(27.0, ReportLayout.generalDataHeight(camposVisibles.size())
                            / Math.max(1, camposVisibles.size())));
        }
        datosTable.setSpacingAfter(10f);
        if (!camposVisibles.isEmpty()) documento.add(datosTable);

        PdfPTable tablaReporte = crearTablaReporte();
        Color colorSeccion = color(template.getSectionBackgroundColor(), GRIS_SECCION);
        agregarSeccion(tablaReporte, template.getSection1Title(),
                reporte.getDatosEquipo(), fuenteSeccion, fuenteNormal, colorSeccion);
        agregarSeccion(tablaReporte, template.getSection2Title(),
                reporte.getDescripcion(), fuenteSeccion, fuenteNormal, colorSeccion);

        List<CategoriaFotografica> categorias = obtenerCategoriasConFotos(reporte);
        if (!categorias.isEmpty()) {
            double espacioDisponible = ReportLayout.initialPhotoSpace(
                    reporte.getDatosEquipo(), reporte.getDescripcion(), template,
                    camposVisibles.size());
            CategoriaFotografica primeraCategoria = categorias.get(0);
            String primerTitulo = valorOVacio(primeraCategoria.getTitulo());
            int finInicial = calcularFinFila(primeraCategoria, 0);
            float[] anchosIniciales = calcularAnchosFila(primeraCategoria, 0, finInicial);
            double bloqueInicial = ReportLayout.estimatePhotoSectionHeight(
                    template.getSection3Title(), false)
                    + ReportLayout.estimateCategoryTitleHeight(primerTitulo,
                    template.getCategoryTitleStyle().getFontSize())
                    + estimarAltoFilaPdf(primeraCategoria, 0, finInicial, anchosIniciales,
                    template.getPhotoCommentStyle())
                    + ReportLayout.PHOTO_SPACING;
            if (bloqueInicial > espacioDisponible) {
                documento.add(tablaReporte);
                documento.newPage();
                tablaReporte = crearTablaReporte();
                espacioDisponible = ReportLayout.CONTENT_HEIGHT;
            }
            agregarEncabezadoFotografico(tablaReporte, fuenteSeccion, false,
                    template.getSection3Title(), colorSeccion);
            espacioDisponible -= ReportLayout.estimatePhotoSectionHeight(
                    template.getSection3Title(), false);

            for (CategoriaFotografica categoria : categorias) {
                String textoCategoria = valorOVacio(categoria.getTitulo());
                int primerFin = calcularFinFila(categoria, 0);
                float[] primerosAnchos = calcularAnchosFila(categoria, 0, primerFin);
                double altoMinimoCategoria = ReportLayout.estimateCategoryTitleHeight(textoCategoria,
                        template.getCategoryTitleStyle().getFontSize())
                        + estimarAltoFilaPdf(categoria, 0, primerFin, primerosAnchos,
                        template.getPhotoCommentStyle())
                        + ReportLayout.PHOTO_SPACING;
                if (altoMinimoCategoria > espacioDisponible) {
                    documento.add(tablaReporte);
                    documento.newPage();
                    tablaReporte = crearTablaReporte();
                    agregarEncabezadoFotografico(tablaReporte, fuenteSeccion, true,
                            template.getSection3Title(), colorSeccion);
                    espacioDisponible = ReportLayout.CONTENT_HEIGHT
                            - ReportLayout.estimatePhotoSectionHeight(
                            template.getSection3Title(), true);
                }
                agregarTituloCategoria(tablaReporte, textoCategoria, fuenteCategoria,
                        template.getCategoryTitleAlignment());
                espacioDisponible -= ReportLayout.estimateCategoryTitleHeight(textoCategoria,
                        template.getCategoryTitleStyle().getFontSize());

                int inicio = 0;
                while (inicio < categoria.getFotografias().size()) {
                    int fin = calcularFinFila(categoria, inicio);
                    float[] anchos = calcularAnchosFila(categoria, inicio, fin);
                    double altoFila = estimarAltoFilaPdf(categoria, inicio, fin, anchos,
                            template.getPhotoCommentStyle())
                            + ReportLayout.PHOTO_SPACING;
                    if (altoFila > espacioDisponible) {
                        documento.add(tablaReporte);
                        documento.newPage();
                        tablaReporte = crearTablaReporte();
                        agregarEncabezadoFotografico(tablaReporte, fuenteSeccion, true,
                                template.getSection3Title(), colorSeccion);
                        String continuacion = textoCategoria + " (continuación)";
                        agregarTituloCategoria(tablaReporte, continuacion, fuenteCategoria,
                                template.getCategoryTitleAlignment());
                        espacioDisponible = ReportLayout.CONTENT_HEIGHT
                                - ReportLayout.estimatePhotoSectionHeight(
                                template.getSection3Title(), true)
                                - ReportLayout.estimateCategoryTitleHeight(continuacion,
                                template.getCategoryTitleStyle().getFontSize());
                    }

                    agregarFilaFotos(tablaReporte, categoria, inicio, fin, anchos,
                            fuenteDescripcionFoto, template.getPhotoCommentStyle());
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
            PdfPTable tabla, Font fuente, boolean continuacion,
            String titulo, Color fondo) {
        String texto = continuacion
                ? valorOVacio(titulo) + " (CONTINUACIÓN)"
                : valorOVacio(titulo);
        PdfPCell encabezado = celdaEncabezado(texto, fuente, fondo);
        encabezado.setMinimumHeight((float) ReportLayout.estimatePhotoSectionHeight(
                titulo, continuacion));
        tabla.addCell(encabezado);
    }

    private static void agregarTituloCategoria(
            PdfPTable tabla, String titulo, Font fuenteCategoria, String alignment) {
        PdfPCell celda = new PdfPCell(new Phrase(titulo, fuenteCategoria));
        celda.setPadding(8f);
        celda.setMinimumHeight((float) ReportLayout.estimateCategoryTitleHeight(
                titulo, fuenteCategoria.getSize()));
        celda.setHorizontalAlignment(pdfAlignment(alignment));
        aplicarBorde(celda);
        tabla.addCell(celda);
    }

    private static double estimarAltoFilaPdf(
            CategoriaFotografica categoria, int inicio, int fin, float[] anchos,
            TextStyle estiloDescripcion)
            throws Exception {
        double alto = 0;
        for (int indice = inicio; indice < fin; indice++) {
            FotoEvidencia foto = categoria.getFotografias().get(indice);
            double ancho = anchos[indice - inicio];
            Image imagen = Image.getInstance(foto.getRuta());
            double altoDescripcion = ReportLayout.estimateDescriptionHeight(
                    foto.getEtiqueta(), ancho, estiloDescripcion.getFontSize());
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
            float[] anchos, Font fuenteDescripcion, TextStyle estiloDescripcion) throws Exception {
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
                categoria, inicio, fin, anchos, estiloDescripcion));
        contenedor.setBorder(Rectangle.LEFT | Rectangle.RIGHT
                | (fin == categoria.getFotografias().size()
                ? Rectangle.BOTTOM : Rectangle.NO_BORDER));
        contenedor.setBorderColor(Color.BLACK);
        contenedor.setBorderWidth(1f);
        tabla.addCell(contenedor);
    }

    private static void agregarEncabezado(
            Document documento, ReporteServicio reporte, TemplateDefinition template) throws Exception {
        String cliente = valorOVacio(reporte.getCliente());
        String empresa = cliente.equals("—") ? "" : cliente.toUpperCase();
        Image logo = cargarLogo(template);
        float logoWidth = (float) template.getHeaderImageWidth();
        float logoHeight = (float) (logoWidth / template.getHeaderImageAspectRatio());
        logo.scaleToFit(logoWidth, logoHeight);
        PdfPTable titulos = new PdfPTable(1);
        titulos.setWidthPercentage(100);
        List<HeaderLine> lineas = template.getHeaderLines();
        if (lineas.isEmpty()) lineas = TemplateDefinition.defaults().getHeaderLines();
        for (HeaderLine linea : lineas) {
            String texto = linea.getText().replace("{empresa}",
                    empresa.isEmpty() ? "____________" : empresa);
            Paragraph paragraph = new Paragraph(texto, crearFuente(linea.getStyle()));
            paragraph.setLeading(0, 1.1f);
            paragraph.setAlignment(pdfAlignment(template.getHeaderTextAlignment()));
            PdfPCell lineCell = new PdfPCell(paragraph);
            lineCell.setBorder(Rectangle.NO_BORDER);
            lineCell.setPadding(0);
            lineCell.setHorizontalAlignment(pdfAlignment(template.getHeaderTextAlignment()));
            titulos.addCell(lineCell);
        }

        PdfPTable encabezado;
        if ("STACKED".equals(template.getHeaderLayout())) {
            encabezado = new PdfPTable(1);
            encabezado.setWidthPercentage(100);
            PdfPCell celdaLogo = new PdfPCell(logo, false);
            celdaLogo.setBorder(Rectangle.NO_BORDER);
            celdaLogo.setPadding(0);
            celdaLogo.setPaddingBottom((float) template.getHeaderGap());
            celdaLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
            encabezado.addCell(celdaLogo);
            PdfPCell celdaTitulo = new PdfPCell(titulos);
            celdaTitulo.setBorder(Rectangle.NO_BORDER);
            celdaTitulo.setPadding(0);
            encabezado.addCell(celdaTitulo);
        } else {
            float textWidth = (float) ReportLayout.estimateHeaderTextWidth(template, empresa);
            float gap = (float) template.getHeaderGap();
            encabezado = new PdfPTable(3);
            encabezado.setWidths(new float[]{logoWidth, Math.max(0.1f, gap), textWidth});
            encabezado.setTotalWidth(logoWidth + Math.max(0.1f, gap) + textWidth);
            encabezado.setLockedWidth(true);
            encabezado.setHorizontalAlignment(Element.ALIGN_CENTER);
            PdfPCell celdaLogo = new PdfPCell(logo, false);
            celdaLogo.setBorder(Rectangle.NO_BORDER);
            celdaLogo.setPadding(0);
            celdaLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
            celdaLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
            encabezado.addCell(celdaLogo);
            PdfPCell spacer = new PdfPCell();
            spacer.setBorder(Rectangle.NO_BORDER);
            encabezado.addCell(spacer);
            PdfPCell celdaTitulo = new PdfPCell(titulos);
            celdaTitulo.setBorder(Rectangle.NO_BORDER);
            celdaTitulo.setPadding(0);
            celdaTitulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
            encabezado.addCell(celdaTitulo);
        }
        encabezado.setSpacingAfter(5f);
        documento.add(encabezado);

        Paragraph linea = new Paragraph();
        linea.setLeading(1f);
        linea.add(new Chunk(new LineSeparator(
                0.5f, 100f, GRIS_LINEA, Element.ALIGN_CENTER, 0)));
        linea.setSpacingAfter(6f);
        documento.add(linea);
    }

    private static Image cargarLogo(TemplateDefinition template) throws Exception {
        if (!template.getHeaderImageBase64().isBlank()) {
            return Image.getInstance(Base64.getDecoder().decode(template.getHeaderImageBase64()));
        }
        return Image.getInstance(App.class.getResource("Imagen12.jpg"));
    }

    private static void agregarSeccion(PdfPTable tabla, String titulo, String contenido,
                                       Font fuenteTitulo, Font fuenteContenido, Color fondo) {
        tabla.addCell(celdaEncabezado(titulo, fuenteTitulo, fondo));
        PdfPCell cuerpo = new PdfPCell(new Phrase(valorOVacio(contenido), fuenteContenido));
        cuerpo.setPaddingTop(8f);
        cuerpo.setPaddingBottom(8f);
        cuerpo.setPaddingLeft(10f);
        cuerpo.setPaddingRight(10f);
        cuerpo.setMinimumHeight((float) ReportLayout.estimateBodyHeight(contenido));
        aplicarBorde(cuerpo);
        tabla.addCell(cuerpo);
    }

    private static PdfPCell celdaEncabezado(String texto, Font fuente, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setBackgroundColor(fondo);
        celda.setPaddingTop(6f);
        celda.setPaddingBottom(6f);
        celda.setPaddingLeft(10f);
        celda.setPaddingRight(10f);
        celda.setMinimumHeight((float) ReportLayout.estimateSectionHeaderHeight(texto));
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
                foto.getEtiqueta(), anchoInterior, fuenteDescripcion.getSize());
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
                                         Font fuenteEtiqueta, Font fuenteValor,
                                         Color fondo, double alto) {
        PdfPCell celdaEtiqueta = new PdfPCell(new Phrase(etiqueta, fuenteEtiqueta));
        celdaEtiqueta.setBackgroundColor(fondo);
        celdaEtiqueta.setPaddingTop(6f);
        celdaEtiqueta.setPaddingBottom(6f);
        celdaEtiqueta.setPaddingLeft(10f);
        celdaEtiqueta.setPaddingRight(10f);
        celdaEtiqueta.setMinimumHeight((float) alto);
        celdaEtiqueta.setBorderColor(Color.GRAY);
        celdaEtiqueta.setBorderWidth(0.5f);

        PdfPCell celdaValor = new PdfPCell(new Phrase(valorOVacio(valor), fuenteValor));
        celdaValor.setBackgroundColor(Color.WHITE);
        celdaValor.setPaddingTop(6f);
        celdaValor.setPaddingBottom(6f);
        celdaValor.setPaddingLeft(10f);
        celdaValor.setPaddingRight(10f);
        celdaValor.setMinimumHeight((float) alto);
        celdaValor.setBorderColor(Color.GRAY);
        celdaValor.setBorderWidth(0.5f);
        tabla.addCell(celdaEtiqueta);
        tabla.addCell(celdaValor);
    }

    private static Map<String, String> valoresCampos(ReporteServicio reporte) {
        Map<String, String> valores = new HashMap<>();
        valores.put("fecha", reporte.getFecha());
        valores.put("area", reporte.getArea());
        valores.put("remision", reporte.getRemision());
        valores.put("cotizacion", reporte.getCotizacion());
        valores.put("factura", reporte.getFactura());
        for (CustomFieldValue value : reporte.getCustomFields()) {
            valores.put(value.getKey(), value.getValue());
        }
        return valores;
    }

    private static Font crearFuente(TextStyle estilo) {
        Path archivo = archivoFuenteWindows(estilo);
        if (archivo != null) {
            try {
                BaseFont base = BaseFont.createFont(archivo.toString(), BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED);
                return new Font(base, (float) estilo.getFontSize(), Font.NORMAL,
                        color(estilo.getColor(), Color.DARK_GRAY));
            } catch (Exception ignored) {
                // Se conserva una fuente estándar si la fuente del sistema no está disponible.
            }
        }
        int familia = switch (estilo.getFontFamily().toLowerCase()) {
            case "times new roman", "times", "serif" -> Font.TIMES_ROMAN;
            case "courier new", "courier", "monospace" -> Font.COURIER;
            default -> Font.HELVETICA;
        };
        int formato = Font.NORMAL;
        if (estilo.isBold()) formato |= Font.BOLD;
        if (estilo.isItalic()) formato |= Font.ITALIC;
        return new Font(familia, (float) estilo.getFontSize(), formato,
                color(estilo.getColor(), Color.DARK_GRAY));
    }

    private static Path archivoFuenteWindows(TextStyle estilo) {
        String regular;
        String bold;
        String italic;
        String boldItalic;
        switch (estilo.getFontFamily().toLowerCase()) {
            case "arial" -> { regular = "arial.ttf"; bold = "arialbd.ttf"; italic = "ariali.ttf"; boldItalic = "arialbi.ttf"; }
            case "calibri" -> { regular = "calibri.ttf"; bold = "calibrib.ttf"; italic = "calibrii.ttf"; boldItalic = "calibriz.ttf"; }
            case "times new roman" -> { regular = "times.ttf"; bold = "timesbd.ttf"; italic = "timesi.ttf"; boldItalic = "timesbi.ttf"; }
            case "verdana" -> { regular = "verdana.ttf"; bold = "verdanab.ttf"; italic = "verdanai.ttf"; boldItalic = "verdanaz.ttf"; }
            case "tahoma" -> { regular = "tahoma.ttf"; bold = "tahomabd.ttf"; italic = "tahoma.ttf"; boldItalic = "tahomabd.ttf"; }
            case "segoe ui" -> { regular = "segoeui.ttf"; bold = "segoeuib.ttf"; italic = "segoeuii.ttf"; boldItalic = "segoeuiz.ttf"; }
            case "courier new" -> { regular = "cour.ttf"; bold = "courbd.ttf"; italic = "couri.ttf"; boldItalic = "courbi.ttf"; }
            default -> { return null; }
        }
        String file = estilo.isBold() && estilo.isItalic() ? boldItalic
                : estilo.isBold() ? bold : estilo.isItalic() ? italic : regular;
        String windows = System.getenv("WINDIR");
        if (windows == null || windows.isBlank()) windows = "C:\\Windows";
        Path path = Path.of(windows, "Fonts", file);
        return Files.isRegularFile(path) ? path : null;
    }

    private static Color color(String hex, Color fallback) {
        try {
            String value = hex == null ? "" : hex.trim().replace("#", "");
            if (value.length() == 8) value = value.substring(0, 6);
            return new Color(Integer.parseInt(value, 16));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int pdfAlignment(String alignment) {
        return switch (alignment == null ? "CENTER" : alignment) {
            case "LEFT" -> Element.ALIGN_LEFT;
            case "JUSTIFY" -> Element.ALIGN_JUSTIFIED;
            default -> Element.ALIGN_CENTER;
        };
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
