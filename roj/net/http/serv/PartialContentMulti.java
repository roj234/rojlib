package roj.net.http.serv;

import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * @author solo6975
 * @since 2022/3/8 0:07
 */
public final class PartialContentMulti implements Response {
    final File file;
    RandomAccessFile raf;
    long[] array;
    int i;
    long remain;
    String splitter;
    ByteBuffer tmp;

    public PartialContentMulti(File f, long[] offsetLengths) {
        if (offsetLengths.length < 2 || (offsetLengths.length & 1) != 0)
            throw new IllegalStateException("Insufficient range");
        file = f;
        this.array = offsetLengths;
        if (offsetLengths.length > 2)
        // maybe collision...
        splitter = Long.toHexString(Double.doubleToRawLongBits(Math.random()));
    }

    @Override
    public void prepare() throws IOException {
        this.raf = new RandomAccessFile(file, "r");
        this.tmp = ByteBuffer.allocate(9999);
        tmp.flip();
    }

    @Override
    public boolean send(RequestHandler ch) throws IOException {
        ByteBuffer t = this.tmp;
        prepare0:
        if (!t.hasRemaining()) {
            if (remain < 0) return false;

            t.clear();
            byte[] arr = t.array();
            int p = 0;

            if (remain == 0) {
                if (array.length > 2) {
                    arr[0] = arr[1] = '-';
                    splitter.getBytes(0, splitter.length(), arr, 2);
                    p = splitter.length() + 2;
                    if (i == array.length) {
                        // end of stream
                        arr[p++] = '-';
                        arr[p] = '-';
                        remain = -1;
                        break prepare0;
                    }
                } else if (i == array.length) {
                    return false;
                }

                raf.seek(array[i]);
                remain = array[i + 1];
                i += 2;
            }

            int read = raf.read(arr, p, (int) Math.min(arr.length - p, remain));
            remain -= read;
            if (p+read>9999 || p+read<0)
            System.out.println(p + "," + read+","+remain+","+i);
            t.position(0).limit(p + read);
        }
        ch.write(t);

        return true;
    }

    @Override
    public void release() throws IOException {
        this.raf.close();
        this.tmp = null;
    }

    @Override
    public void writeHeader(ByteList list) {
        int i = 0;
        long l = 0;
        while (i < array.length) {
            l += array[i+1];
            i += 2;
        }

        list.putAscii("Last-Modified: ").putAscii(RequestHandler.LocalShared.get().date.toRFCDate(file.lastModified())).putAscii(CRLF)
            .putAscii("Content-Length: ").putAscii(Long.toString(l)).putAscii(CRLF);
        if (array.length == 2) {
            list.putAscii("Content-Range: ").putAscii(Long.toString(array[i])).put((byte) '-').putAscii(Long.toString(array[i] + array[i+1] - 1))
                .putAscii(CRLF);
        }
        if (array.length > 2)
            list.putAscii("Content-Type: multipart/byteranges; boundary=").putAscii(splitter).putAscii(CRLF);
    }
}