package com.spacesim.persistence;

/**
 * A campaign save written in a format this build does not load. Dev formats before the first
 * release candidate are not supported; the file is rejected before anything is restored.
 */
public final class UnsupportedSaveVersionException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    /** Version found in the file, or -1 when the file is not a campaign save. */
    private final int fileVersion;
    /** Version this build writes and loads. */
    private final int currentVersion;

    /**
     * @param fileVersion version found in the file, or -1 when the file is not a campaign save
     * @param currentVersion version this build writes and loads
     */
    public UnsupportedSaveVersionException(int fileVersion, int currentVersion) {
        super(fileVersion < 0 ? "Not a current M22.8 campaign save"
                : "Unsupported dev campaign save v" + fileVersion + "; this build loads v" + currentVersion);
        this.fileVersion = fileVersion;
        this.currentVersion = currentVersion;
    }

    /** @return version found in the file, or -1 when the file is not a campaign save */
    public int fileVersion() {
        return fileVersion;
    }

    /** @return version this build writes and loads */
    public int currentVersion() {
        return currentVersion;
    }
}
