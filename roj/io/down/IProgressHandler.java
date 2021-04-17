package roj.io.down;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/9/13 12:06
 */
public interface IProgressHandler {
    static IProgressHandler getNotify(boolean notify) {
        return new Notify(notify);
    }

    default void handleJoin(Downloader downloader) {}

    default void handleProgress(Downloader thread, long downloaded, long deltaDone) {}

    default void handleReconnect(Downloader thread, long downloaded) {}

    default void handleDone(Downloader thread) {}

    default void onReturn() {}

    /**
     * only for multi thread
     *
     * @param i count
     */
    default void onInitial(int i) {}

    void errorCaught();

    boolean continueDownload();
}
