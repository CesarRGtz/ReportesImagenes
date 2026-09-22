package com.teosa.app.prototipo.infrastructure.pdf;

import com.teosa.app.prototipo.domain.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class QuotationPdfGenerator {
    private QuotationPdfGenerator() {}

    public static void generate(Path target, Quotation quotation, TemplateDefinition template) throws Exception {
        try (OutputStream output = Files.newOutputStream(target)) {
            Document document = new Document(PageSize.LETTER, 36, 36, 32, 64);
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter w, Document d) {
                    footerLine(w,d,template.getQuotationFooterAddress(),42);
                    footerLine(w,d,template.getQuotationFooterContact(),30);
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase("Cotización " + quotation.value("folio") + " · Página " + w.getPageNumber(),
                                    new Font(Font.HELVETICA, 7)), d.right(), 14, 0);
                }
            });
            document.open();
            try {
                PdfReportGenerator.agregarEncabezado(document, quotation.value("cliente"), template);
                Font body = PdfReportGenerator.crearFuente(template.getPhotoCommentStyle());
                Font heading = new Font(body); heading.setStyle(Font.BOLD);
                Color background = color(template.getSectionBackgroundColor());
                FieldDefinition folio=template.getFields().get("folio");
                if(folio!=null && folio.isVisible()){
                    Paragraph reference=new Paragraph(folio.getLabel()+"  "+quotation.value("folio"),heading);
                    reference.setAlignment(Element.ALIGN_RIGHT);document.add(reference);
                }
                Paragraph intro = new Paragraph(quotation.getIntroduction(), body);
                intro.setSpacingBefore(6); intro.setSpacingAfter(12); document.add(intro);

                PdfPTable metadata = new PdfPTable(new float[]{35, 30, 15, 20});
                metadata.setWidthPercentage(100); metadata.setSpacingAfter(12);
                int fields = 0;
                for (FieldDefinition field : template.orderedFields()) {
                    if (!field.isVisible() || field.getKey().equals("folio")) continue;
                    int span=field.getKey().equals("servicio")?2:1;
                    if(fields%4+span>4){while(fields%4!=0){metadata.addCell(cell("",body,Color.WHITE,false));fields++;}}
                    PdfPTable entry=new PdfPTable(1);
                    entry.addCell(cell(field.getLabel(),heading,color(field.getBackgroundColor()),false));
                    entry.addCell(cell(quotation.value(field.getKey()),field.getKey().equals("servicio")?heading:body,Color.WHITE,false));
                    PdfPCell entryCell=new PdfPCell(entry);entryCell.setPadding(0);entryCell.setColspan(span);
                    metadata.addCell(entryCell);fields+=span;
                }
                while(fields%4!=0){metadata.addCell(cell("",body,Color.WHITE,false));fields++;}
                document.add(metadata);

                PdfPTable items = new PdfPTable(new float[]{0.8f, 4.7f, 0.7f, 1.15f, 1.15f});
                items.setWidthPercentage(100); items.setHeaderRows(1); items.setSplitLate(false);
                for(String title : new String[]{"CLAVE", template.getSection1Title(), "CANT.", "COSTO UNITARIO", "IMPORTE"}) {
                    items.addCell(cell(title, heading, background, false));
                }
                for (QuotationLine line : quotation.getLines()) {
                    boolean scopes=!line.getScopes().isEmpty();
                    int firstBorder=Rectangle.LEFT|Rectangle.RIGHT|Rectangle.TOP|(scopes?0:Rectangle.BOTTOM);
                    items.addCell(itemCell(line.getCode(),heading,false,firstBorder));
                    items.addCell(itemCell(line.getDescription(),heading,false,firstBorder));
                    items.addCell(itemCell(line.getQuantity().stripTrailingZeros().toPlainString(),body,true,firstBorder));
                    items.addCell(itemCell(money(line.getUnitPrice()),body,true,firstBorder));
                    items.addCell(itemCell(money(line.amount()),body,true,firstBorder));
                    if(scopes){
                        int sides=Rectangle.LEFT|Rectangle.RIGHT;
                        items.addCell(itemCell("",body,false,sides));
                        items.addCell(itemCell(template.getSection2Title()+":",heading,false,sides));
                        for(int c=0;c<3;c++)items.addCell(itemCell("",body,false,sides));
                        for(int i=0;i<line.getScopes().size();i++){
                            QuotationScope scope=line.getScopes().get(i);
                            int border=sides|(i==line.getScopes().size()-1?Rectangle.BOTTOM:0);
                            items.addCell(itemCell(QuotationScope.code(i),body,false,border));
                            PdfPTable description=new PdfPTable(line.isScopeBreakdown()?new float[]{3.5f,1.2f}:new float[]{1});
                            description.setWidthPercentage(100);description.setSplitLate(false);
                            description.addCell(itemCell(scope.getDescription(),body,false,Rectangle.NO_BORDER));
                            if(line.isScopeBreakdown())description.addCell(itemCell(money(scope.getCost()),body,true,Rectangle.NO_BORDER));
                            PdfPCell detail=new PdfPCell(description);detail.setPadding(0);detail.setBorder(border);detail.setBorderWidth(0.5f);
                            items.addCell(detail);
                            for(int c=0;c<3;c++)items.addCell(itemCell("",body,false,border));
                        }
                    }
                }
                document.add(items);
                if (quotation.getNotes()!=null && !quotation.getNotes().isBlank()) {
                    Paragraph notesTitle=new Paragraph(template.getSection3Title()+":", heading);
                    notesTitle.setSpacingBefore(12); document.add(notesTitle);
                    document.add(new Paragraph(quotation.getNotes(), body));
                }
                PdfPTable totals = new PdfPTable(new float[]{3.8f,1.2f,1.2f});
                totals.setWidthPercentage(100); totals.setSpacingBefore(16); totals.setKeepTogether(true);
                PdfPCell words=cell("CANTIDAD CON LETRA:\n"+AmountInWords.pesos(quotation.total()),body,Color.WHITE,false);
                words.setRowspan(3); totals.addCell(words);
                totals.addCell(cell("SUBTOTAL", heading, Color.WHITE,false)); totals.addCell(cell(money(quotation.subtotal()),body,Color.WHITE,true));
                totals.addCell(cell("IVA "+quotation.getTaxRate().movePointRight(2).stripTrailingZeros().toPlainString()+"%",heading,Color.WHITE,false));
                totals.addCell(cell(money(quotation.tax()),body,Color.WHITE,true));
                totals.addCell(cell("TOTAL",heading,color(template.getTotalBackgroundColor()),false));totals.addCell(cell(money(quotation.total()),heading,color(template.getTotalBackgroundColor()),true));
                document.add(totals);
            } finally { document.close(); }
        }
    }
    private static void footerLine(PdfWriter writer,Document document,String value,float y){
        String text=value.replace('\n',' ').replace('\r',' ');
        Font font=new Font(Font.HELVETICA,8.5f);
        float width=font.getCalculatedBaseFont(false).getWidthPoint(text,font.getSize());
        float available=document.right()-document.left();
        if(width>available)font.setSize(font.getSize()*available/width);
        ColumnText.showTextAligned(writer.getDirectContent(),Element.ALIGN_CENTER,new Phrase(text,font),
                (document.left()+document.right())/2,y,0);
    }
    private static PdfPCell itemCell(String value,Font font,boolean right,int border){
        PdfPCell result=cell(value,font,Color.WHITE,right);result.setBorder(border);return result;
    }
    private static PdfPCell cell(String value, Font font, Color background, boolean right) {
        PdfPCell cell = new PdfPCell(new Phrase(value==null?"":value,font));
        cell.setPadding(5); cell.setBackgroundColor(background); cell.setBorderWidth(0.5f);
        cell.setHorizontalAlignment(right?Element.ALIGN_RIGHT:Element.ALIGN_LEFT);
        return cell;
    }
    public static String money(BigDecimal value) {return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(value);}
    private static Color color(String hex) {try{return Color.decode(hex);}catch(Exception ex){return Color.LIGHT_GRAY;}}
}
