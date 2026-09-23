package app.jmclean.core;

import io.github.jukomu.jmcomic.api.enums.ClientType;
import io.github.jukomu.jmcomic.api.model.JmAlbum;
import io.github.jukomu.jmcomic.core.JmComic;
import io.github.jukomu.jmcomic.core.client.AbstractJmClient;
import io.github.jukomu.jmcomic.core.config.JmConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

public final class ComicDownloadService {
    private ComicDownloadService() {}

    public static List<String> parseIds(String text) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (text != null) {
            for (String token : text.split("[^0-9]+")) {
                if (!token.isBlank()) ids.add(token);
            }
        }
        return new ArrayList<>(ids);
    }

    public static void download(List<String> ids, Path destination, Consumer<DownloadStatus> callback) throws Exception {
        if (ids.isEmpty()) throw new IllegalArgumentException("请输入漫画编号");
        Files.createDirectories(destination);
        JmConfiguration config = new JmConfiguration.Builder()
                .clientType(ClientType.API)
                .downloadThreadPoolSize(Math.max(3, Math.min(8, Runtime.getRuntime().availableProcessors())))
                .build();

        int doneAlbums = 0;
        try (AbstractJmClient client = JmComic.newApiClientAsync(config).join()) {
            for (String id : ids) {
                try {
                    callback.accept(new DownloadStatus(id, "", 0, 0, doneAlbums, ids.size(), "正在读取 " + id, false, false));
                    JmAlbum album = client.getAlbum(id);
                    String title = album.getTitle();
                    Path albumFolder = destination.resolve(safeName(id + " - " + title));
                    var result = client.download(album)
                            .withPath(albumFolder)
                            .withProgress(p -> callback.accept(new DownloadStatus(
                                    id, title, p.completedImages(), p.totalImages(),
                                    p.completedPhotos(), p.totalPhotos(), "正在下载", false, false)))
                            .execute();
                    if (!result.isAllSuccess()) {
                        throw new IllegalStateException("部分图片下载失败：" + result.getFailedTasks().size());
                    }
                    doneAlbums++;
                    callback.accept(new DownloadStatus(id, title, 1, 1, doneAlbums, ids.size(), "已完成", false, false));
                } catch (Exception e) {
                    callback.accept(new DownloadStatus(id, "", 0, 0, doneAlbums, ids.size(), cleanError(e), false, true));
                }
            }
        }
        callback.accept(new DownloadStatus("", "", 1, 1, doneAlbums, ids.size(), "全部任务结束", true, doneAlbums == 0));
    }

    private static String safeName(String value) {
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private static String cleanError(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        return "失败：" + (message == null || message.isBlank() ? current.getClass().getSimpleName() : message);
    }
}
