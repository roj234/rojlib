package roj.net.http;

import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.net.NetworkUtil;
import roj.net.PlainSocket;
import roj.net.WrappedSocket;
import roj.util.ByteList;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * @author Roj233
 * @since 2022/3/12 19:02
 */
public class ChunkedSocket implements WrappedSocket {
    private final WrappedSocket ch;

    private byte flag;
    private int inSt, outSt;
    private final byte[] hex = new byte[8];
    private ByteBuffer inTmp;

    public ChunkedSocket(WrappedSocket ch) {
        this.ch = ch;
        this.inTmp = PlainSocket.EMPTY;
    }

    @Override
    public WrappedSocket parent() {
        return ch;
    }

    @Override
    public Socket socket() {
        return ch.socket();
    }

    @Override
    public boolean handShake() throws IOException {
        return ch.handShake();
    }

    @Override
    public int read() throws IOException {
        if ((flag & 4) == 0) return ch.read();
        int data = inSt;
        if (data < 0) return -1;
        int r = ch.read(data == 0 ? ch.buffer().capacity()+1 : data);

        ByteBuffer buf = ch.buffer();
        int pos = buf.position();
        if (pos == 0) return r;

        int op = inTmp.position();

        try {
            while (true) {
                buf.position(0).limit(Math.min(data, pos));
                if (inTmp.remaining() < buf.remaining()) allocateNew(buf.remaining());
                inTmp.put(buf);
                data -= buf.limit();
    
                buf.position(buf.limit()).limit(pos);
                buf.compact();
                pos = buf.position();
    
                if (data > 0) break;
    
                if (pos < 3) return 0;
                int i = 0;
    
                // end of chunk
                if ((flag & 1) != 0) {
                    if (buf.get(i++) != '\r' || buf.get(i++) != '\n')
                        throw new IOException("IllegalChunkEncoding");
                }
    
                // chunk length
                int j = i;
                while (buf.get(j++) != '\r') {
                    if (j == pos) return 0;
                }
                if (j == pos) return 0;
                if (buf.get(j++) != '\n') throw new IOException("IllegalChunkEncoding");
    
                ByteList tmp = IOUtil.getSharedByteBuf();
    
                buf.position(i);
                buf.get(tmp.list, 0, j - i - 2)
                   .position(pos);
                tmp.wIndex(j - i - 2);
    
                data = MathUtils.parseInt(tmp, 0, tmp.wIndex(), 16);
                if (data == 0) {
                    if (j + 2 >= buf.position()) return 0;
                    if (buf.get(j++) != '\r' || buf.get(j) != '\n')
                        throw new IOException("IllegalChunkEncoding");
                    inSt = -1;
                    break;
                } else {
                    buf.position(j).limit(pos);
                    buf.compact();
                    pos = buf.position();
                }
                flag |= 1;
            }
        } finally {
            inSt = data;
        }

        return inTmp.position() - op;
    }

    private void allocateNew(int add) {
        ByteBuffer cur = inTmp;
        ByteBuffer next = ByteBuffer.allocate(Math.max(cur.remaining() + add, 1024));
        cur.flip();
        next.put(cur);
        inTmp = next;
    }

    @Override
    public int read(int max) throws IOException {
        if ((flag & 4) == 0) return ch.read(max);
        return read();
    }

    public ByteBuffer getBuffer(int i) {
        allocateNew(i);
        return inTmp;
    }

    @Override
    public ByteBuffer buffer() {
        return (flag & 4) == 0 ? ch.buffer() : inTmp;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public int write(ByteBuffer src) throws IOException {
        if ((flag & 2) == 0) return ch.write(src);
        if (outSt < 0) return outSt;
        int w = 0;
        ByteBuffer b = ch.buffer();
        switch (outSt) {
            case 0:
                byte[] hex = this.hex;
                int v = src == null ? 0 : src.remaining();
                //if (v == 0)
                int off = NetworkUtil.number2hex(v, hex);

                int p = b.position();
                b.put(hex, off, 8 - off).putChar((char) 0x0D0A)
                 .flip().position(p).mark();
                outSt = 1;
            case 1:
                int w2 = ch.write(b);
                if (w2 < 0) return outSt = w2;
                if (b.hasRemaining()) return 0;
                b.reset().limit(b.capacity());
                this.hex[0] = (byte) (src != null && src.hasRemaining() ? 1 : 0);
                if (src != null) {
                    outSt = 2;
                } else {
                    outSt = 3;
                    return write(null);
                }
            case 2:
                w = ch.write(src);
                if (w < 0) return outSt = w;
                if (src.hasRemaining()) return w;
                outSt = 3;
            case 3:
                b.putChar((char) 0x0D0A).flip()
                 .position(b.limit() - 2).mark();
            case 4:
                int w1 = ch.write(b);
                if (w1 < 0) return outSt = w1;
                if (b.hasRemaining()) return w;
                b.reset().limit(b.capacity());
                if (this.hex[0] <= 0) return outSt = -1;
                else outSt = 0;
        }
        return w;
    }

    @Override
    public boolean dataFlush() throws IOException {
        if (outSt > 2) write(null);
        return ch.dataFlush();
    }

    @Override
    public boolean shutdown() throws IOException {
        inSt = outSt = -1;
        return ch.shutdown();
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public void close() throws IOException {
        ch.close();
    }

    @Override
    public void reset() throws IOException {
        ch.reset();
        inSt = outSt = 0;
        flag = 0;
        inTmp.clear();
    }

    public void resetSelf() {
        inSt = outSt = 0;
        flag = 0;
        inTmp.clear();
    }

    public void enableIn() {
        flag |= 4;
    }

    public void enableOut() {
        flag |= 2;
    }

    @Override
    public FileDescriptor fd() {
        return ch.fd();
    }
}
