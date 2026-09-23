package app.jmclean.core;

public record DownloadStatus(
        String albumId,
        String title,
        int completedImages,
        int totalImages,
        int completedAlbums,
        int totalAlbums,
        String message,
        boolean finished,
        boolean failed) {

    public int percent() {
        if (finished && !failed) return 100;
        if (totalImages <= 0) return 0;
        return Math.max(0, Math.min(99, completedImages * 100 / totalImages));
    }
}
