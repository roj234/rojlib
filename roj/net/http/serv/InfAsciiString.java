package roj.net.http.serv;

import roj.concurrent.OperationDone;

import java.nio.ByteBuffer;

/**
 * @author Roj233
 * @since 2022/3/16 19:11
 */
class InfAsciiString implements CharSequence {
    ByteBuffer buf;

    public InfAsciiString(ByteBuffer buf) {
        this.buf = buf;
    }

    @Override
    public int length() {
        return Integer.MAX_VALUE;
    }

    @Override
    public char charAt(int i) {
        if (buf.remaining() <= i) throw OperationDone.INSTANCE;
        return (char) buf.get(buf.position() + i);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        ByteBuffer b1 = buf.duplicate();
        b1.limit(buf.position() + end).position(buf.position() + start);
        return new InfAsciiString(b1);
    }

    @Override
    public String toString() {
        byte[] data = new byte[buf.remaining()];
        buf.get(data).position(buf.position() - data.length);
        return new String(data, 0);
    }
}
