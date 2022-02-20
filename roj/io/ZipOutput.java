package roj.io;

import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;

/**
 * @author Roj233
 * @since 2022/2/22 13:14
 */
public class ZipOutput {
    public final File file;
    private MutableZipFile some;
    private ZipFileWriter all;
    private boolean useZFW, compress, work;

    public ZipOutput(File file) {
        this.file = file;
    }

    public void setCompress(boolean compress) {
        this.compress = compress;
    }

    public void begin(boolean allModifyMode) throws IOException {
        if (work) end();

        useZFW = allModifyMode;
        if (allModifyMode) {
            all = new ZipFileWriter(file, false);
            if (some != null) {
                some.close();
                some = null;
            }
        } else if (some == null) {
            some = new MutableZipFile(file);
        } else {
            some.reopen();
        }
        work = true;
    }

    public void setComment(String comment) {
        if (useZFW) {
            all.setComment(comment);
        } else {
            some.getEOF().setComment(comment);
        }
    }

    public void set(String name, ByteList data) throws IOException {
        if (useZFW) {
            all.writeNamed(name, data, compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
        } else {
            some.put(name, data).compress = compress;
        }
    }

    public void set(String name, Supplier<ByteList> data) throws IOException {
        if (useZFW) {
            all.writeNamed(name, data.get(), compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
        } else {
            some.put(name, data, compress);
        }
    }

    public void set(String name, InputStream data) throws IOException {
        if (useZFW) {
            ZipEntry ze = new ZipEntry(name);
            ze.setMethod(compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
            all.beginEntry(ze);

            try {
                ByteList b = IOUtil.getSharedByteBuf();
                b.ensureCapacity(4096);
                byte[] list = b.list;
                int cnt;
                do {
                    cnt = data.read(list);
                    all.write(list, 0, cnt);
                } while (cnt == list.length);
                all.closeEntry();
            } finally {
                data.close();
            }
        } else {
            some.putStream(name, data, compress);
        }
    }

    public void end() throws IOException {
        if (useZFW) {
            if (all != null) {
                all.finish();
                all = null;
            }
        } else if (some != null) {
            some.store();
            some.tClose();
        }
        work = false;
    }

    public MutableZipFile getMZF() throws IOException {
        if (some == null) {
            return new MutableZipFile(file, MutableZipFile.FLAG_READ_ATTR);
        } else {
            if (!some.isOpen()) {
                some.reopen();
            }
        }
        return some;
    }

    public void close() throws IOException {
        end();
        if (some != null) {
            some.close();
            some = null;
        }
    }
}
