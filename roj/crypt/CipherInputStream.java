package roj.crypt;

import roj.io.buf.BufferPool;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2022/11/12 0012 15:27
 */
public class CipherInputStream extends InputStream {
	protected InputStream in;
	boolean eof;

	private byte[] b1;

	private final ByteList.Slice i = new ByteList.Slice();
	private ByteList o;

	protected final RCipherSpi c;
	private int cw,cr;

	public CipherInputStream(InputStream in, RCipherSpi c) {
		this.in = in;
		this.c = c;

		this.o = (ByteList) BufferPool.localPool().buffer(false, CipherOutputStream.BUFFER_SIZE);
		this.i.set(o.array(), o.arrayOffset(), o.capacity());
	}

	@Override
	public int read() throws IOException {
		if (b1 == null) b1 = new byte[1];
		return read(b1,0,1) > 0 ? b1[0]&0xFF : -1;
	}

	@Override
	public int read(@Nonnull byte[] b, int off, int len) throws IOException {
		if (eof) return -1;

		int myLen = len;
		try {
			while (true) {
				if (o.readableBytes() >= myLen) {
					o.read(b, off, myLen);
					return len;
				}

				int avl = o.readableBytes();
				o.read(b, off, avl);
				off += avl;
				myLen -= avl;

				o.rIndex = cr;
				o.wIndex(cw);
				o.compact();

				i.clear();

				if (o.readStream(in, o.writableBytes()) <= 0) {
					eof = true;

					if (!o.isReadable()) {
						close();
						return len-myLen;
					}

					c.cryptFinal(o, i);
					if (o.isReadable()) throw new IOException("Cipher拒绝处理 " + o.readableBytes() + " 大小的块");
				} else {
					c.crypt(o, i);
				}

				// hide ciphertext
				cr = o.rIndex;
				cw = o.wIndex();

				o.rIndex = 0;
				o.wIndex(i.wIndex());
			}
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			Helpers.athrow(e);
			return -1;
		}
	}

	@Override
	public int available() throws IOException {
		if (eof) return o.isReadable()?o.readableBytes():-1;
		else return o.readableBytes()+in.available();
	}

	@Override
	public void close() throws IOException {
		try {
			in.close();
		} finally {
			synchronized (this) {
				if (o != null) o.close();
				o = null;
			}
		}
	}
}
