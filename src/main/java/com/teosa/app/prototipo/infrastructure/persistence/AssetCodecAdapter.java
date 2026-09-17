package com.teosa.app.prototipo.infrastructure.persistence;
import com.teosa.app.prototipo.application.port.AssetCodec;
import com.teosa.app.prototipo.domain.*;
import java.io.IOException;
public final class AssetCodecAdapter implements AssetCodec {
 public ReportTransfer pack(ReportSnapshot s)throws IOException{return AssetManager.pack(s);}
 public ReportSnapshot materialize(ReportTransfer t)throws IOException{return AssetManager.materialize(t);}
}
