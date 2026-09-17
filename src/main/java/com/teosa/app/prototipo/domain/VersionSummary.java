package com.teosa.app.prototipo.domain;

public class VersionSummary {
    private int version;
    private long savedAt;
    private String author;
    private String computer;
    private boolean pending;
    private String localId;

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public long getSavedAt() { return savedAt; }
    public void setSavedAt(long savedAt) { this.savedAt = savedAt; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getComputer() { return computer; }
    public void setComputer(String computer) { this.computer = computer; }
    public boolean isPending() { return pending; }
    public void setPending(boolean pending) { this.pending = pending; }
    public String getLocalId() { return localId; }
    public void setLocalId(String localId) { this.localId = localId; }
}
