package roj.crypt;

import org.jetbrains.annotations.NotNull;
import roj.io.Finishable;
import roj.io.buf.BufferPool;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

/**
 * @author Roj234
 * @since 2022/11/12 0012 15:27
 */
public class CipherOutputStream extends FilterOutputStream implements Finishable {
	static final int BUFFER_SIZE = 1024;

	private byte[] b1;

	private final ByteList.Slice i = new ByteList.Slice();
	private ByteList o;

	protected final RCipherSpi c;
	protected final int block;

	public CipherOutputStream(OutputStream out, RCipherSpi c) {
		super(out);
		this.c = c;

		if (c.engineGetBlockSize() != 0) {
			int len = BUFFER_SIZE;
			while (c.engineGetOutputSize(len) > BUFFER_SIZE)
				len -= c.engineGetBlockSize();

			o = (ByteList) BufferPool.buffer(false, len);
			i.set(o.array(),o.arrayOffset(),o.capacity());
			block = len;
		} else {
			o = new ByteList.Slice();
			block = 0;
		}
	}

	public void write(int i) throws IOException {
		if (b1 == null) b1 = new byte[1];
		b1[0] = (byte) i;
		write(b1, 0, 1);
	}
	public void write(@NotNull byte[] b, int off, int len) throws IOException {
		if (out == null) throw new IOException("Stream closed");

		try {
			ByteList ob = o;

			if (block == 0) {
				c.crypt(i.setR(b, off, len), ((ByteList.Slice)ob).set(b, off, len));
				out.write(b, off, len);
				return;
			}

			while (len > 0) {
				if (len < ob.writableBytes()) {
					ob.put(b,off,len);
					return;
				}

				int avl = ob.writableBytes();
				ob.put(b,off,avl);
				off += avl;
				len -= avl;

				flush();
			}
		} catch (Throwable e) {
			close();
			Helpers.athrow(e);
		}
	}

	public void flush() throws IOException {
		if (block == 0) return;

		ByteList ib = i;
		ByteList ob = o;
		ib.clear();
		try {
			c.crypt(ob, ib);
		} catch (GeneralSecurityException e) {
			Helpers.athrow(e);
		}
		ib.writeToStream(out);
		ob.compact();
	}

	@Override
	public void finish() throws IOException {
		if (block != 0 && o.isReadable()) {
			flush();
			try {
				finalBlock(o);
			} catch (Exception e) {
				Helpers.athrow(e);
			}
		}

		if (o != null) {
			if (BufferPool.isPooled(o))
				BufferPool.reserve(o);
			o = null;
		}

		out.flush();
	}

	public synchronized void close() throws IOException {
		if (out == null) return;
		try {
			finish();
		} finally {
			out.close();
			out = null;
		}
	}

	protected void finalBlock(ByteList o) throws Exception {
		int blockSize = c.engineGetBlockSize()-1;
		if (blockSize > 0) {
			int off = o.wIndex();
			int end = 0 == (off&blockSize) ? off : (off|blockSize)+1;

			o.wIndex(end);
			off += o.arrayOffset();
			end += o.arrayOffset();

			// zero padding
			byte[] buf = o.array();
			while (off < end) buf[off++] = 0;
		}

		// filter mode: may grab some byte and process, left remain unchanged
		i.clear();
		c.cryptFinal(o, i);
		i.writeToStream(out);

		if (o.isReadable()) throw new IOException("data remain:"+o.dump());
	}
}