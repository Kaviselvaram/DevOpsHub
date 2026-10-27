package com.devopshub.model;

/**
 * Verified metadata for genuine build and packaging artifacts.
 */
public class ArtifactInfo {
    private final String name;
    private final String type;
    private final String filePath;
    private final long sizeBytes;
    private final String sha256Checksum;
    private final String producingStage;
    private final boolean verified;

    public ArtifactInfo(String name, String type, String filePath, long sizeBytes,
                        String sha256Checksum, String producingStage, boolean verified) {
        this.name = name;
        this.type = type;
        this.filePath = filePath;
        this.sizeBytes = sizeBytes;
        this.sha256Checksum = sha256Checksum != null ? sha256Checksum : "N/A (Directory or Archive)";
        this.producingStage = producingStage != null ? producingStage : "Build / Packaging";
        this.verified = verified;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public String getFilePath() { return filePath; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256Checksum() { return sha256Checksum; }
    public String getProducingStage() { return producingStage; }
    public boolean isVerified() { return verified; }

    public String getFormattedSize() {
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format("%.1f KB", sizeBytes / 1024.0);
        return String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0));
    }
}
