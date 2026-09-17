package com.teosa.app.prototipo.infrastructure.pdf;
import com.teosa.app.prototipo.domain.*;
import static com.teosa.app.prototipo.domain.CuadroFirmas.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.util.Base64;
public final class SignaturePdf {
    public static PdfPTable pdf(FirmasReporte f) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setKeepTogether(true);
        table.setSplitRows(false);
        table.setSpacingBefore((float) ESPACIO);
        for (String encoded : new String[]{f.getImagenElaboro(), f.getImagenVerifico()}) {
            PdfPCell cell = celda("", 112);
            if (!encoded.isBlank()) {
                Image image = Image.getInstance(Base64.getDecoder().decode(encoded));
                image.scaleToFit((float) (ANCHO / 2 - 16), 96);
                cell = new PdfPCell(image, false);
                cell.setPadding(8); cell.setFixedHeight(112);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setBorderWidth(0.75f);
            }
            table.addCell(cell);
        }
        table.addCell(celda("Elaboró", 22)); table.addCell(celda("Verificó", 22));
        table.addCell(celda("Supervisor", 32)); table.addCell(celda("Responsable", 32));
        table.addCell(celda(f.getNombreElaboro(), altoNombre(f)));
        table.addCell(celda(f.getNombreVerifico(), altoNombre(f)));
        return table;
    }
    private static PdfPCell celda(String text, double height) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 11)));
        cell.setMinimumHeight((float) height); cell.setPadding(3);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER); cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderWidth(0.75f);
        return cell;
    }
}
