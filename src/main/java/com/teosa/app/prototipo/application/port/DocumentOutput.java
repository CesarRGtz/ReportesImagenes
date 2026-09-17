package com.teosa.app.prototipo.application.port;
import com.teosa.app.prototipo.domain.*;
import java.io.File;
import java.util.List;
public interface DocumentOutput {
    void report(File file, ReporteServicio report, TemplateDefinition template) throws Exception;
    void quotation(File file, Quotation quotation, TemplateDefinition template) throws Exception;
    List<byte[]> preview(Quotation quotation, TemplateDefinition template) throws Exception;
}
