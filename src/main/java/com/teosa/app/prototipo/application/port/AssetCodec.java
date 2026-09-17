package com.teosa.app.prototipo.application.port;
import com.teosa.app.prototipo.domain.*;
import java.io.IOException;
public interface AssetCodec {
 ReportTransfer pack(ReportSnapshot snapshot) throws IOException;
 ReportSnapshot materialize(ReportTransfer transfer) throws IOException;
}
