package com.teosa.app.prototipo.data;

import java.util.ArrayList;
import java.util.List;

public class ReportTransfer {
    private ReportSnapshot snapshot;
    private List<AssetPayload> assets = new ArrayList<>();

    public ReportSnapshot getSnapshot() { return snapshot; }
    public void setSnapshot(ReportSnapshot snapshot) { this.snapshot = snapshot; }
    public List<AssetPayload> getAssets() { if (assets == null) assets = new ArrayList<>(); return assets; }
    public void setAssets(List<AssetPayload> assets) { this.assets = assets; }
}
