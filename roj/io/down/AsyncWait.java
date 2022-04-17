package roj.io.down;

import roj.concurrent.Waitable;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author solo6975
 * @since 2022/5/1 0:55
 */
final class AsyncWait implements Waitable {
    private final List<IDown> tasks;
    private final IProgress   handler;
    private final File        file;

    AsyncWait(List<IDown> tasks, IProgress handler, File file) {
        this.tasks = tasks;
        this.handler = handler;
        this.file = file;
    }

    @Override
    public void waitFor() throws IOException {
        for (IDown task : tasks) {
            synchronized (task) {
                try {
                    task.waitFor();
                } catch (InterruptedException ignored) {
                }
            }
        }

        if (handler != null) {
            if (handler.wasShutdown()) {
                throw new IOException("下载失败");
            }
            handler.onFinish();
        }

        StringBuilder err = new StringBuilder();

        File tag = new File(file.getAbsolutePath() + ".tag");
        if (tag.isFile() && !tag.delete()) {
            tag.deleteOnExit();
            err.append("; ETag标记删除失败. ");
        }

        File info = new File(file.getAbsolutePath() + ".nfo");
        if (info.isFile() && !info.delete()) {
            info.deleteOnExit();
            err.append("; 下载进度删除失败. ");
        }

        File tempFile = new File(file.getAbsolutePath() + ".tmp");
        if (!tempFile.renameTo(file)) {
            if (file.isFile()) {
                err.append("; 文件已被另一个线程完成.");
            } else {
                err.append("; 文件重命名失败.");
            }
        }

        if (err.length() > 0) {
            throw new IOException(err.toString());
        }
    }

    @Override
    public boolean isDone() {
        boolean done = true;
        for (int i = 0; i < tasks.size(); i++) {
            IDown task = tasks.get(i);
            if (task.getRemain() > 0) return false;
        }
        return true;
    }

    @Override
    public void cancel() {
        for (int i = 0; i < tasks.size(); i++) {
            try {
                tasks.get(i).close();
            } catch (IOException e) {
                // should not happen
            }
        }
    }

    @Override
    public String toString() {
        return tasks.toString();
    }
}
