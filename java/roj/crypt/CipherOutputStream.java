package roj.crypt;

import org.jetbrains.annotations.NotNull;
import roj.io.Finishable;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

/**
 * @author Roj234
 * @since 2022/11/12 15:27
 */
public class CipherOutputStream extends OutputStream implements Finishable {
	static final int BUFFER_SIZE = 1024;

	protected OutputStream out;

	private final ByteList.Slice inputBuffer = new ByteList.Slice();
	private ByteList outputBuffer;

	protected final RCipherSpi cipher;
	protected final int blockSize;

	public CipherOutputStream(OutputStream out, RCipherSpi cipher) {
		this.out = out;
		this.cipher = cipher;

		int blockSize1 = cipher.engineGetBlockSize();
		if (blockSize1 != 0) {
			// 计算最佳缓冲区大小
			int bufferSize = BUFFER_SIZE;
			while (cipher.engineGetOutputSize(bufferSize) > BUFFER_SIZE) {
				bufferSize -= blockSize1;
			}

			outputBuffer = (ByteList) BufferPool.buffer(false, bufferSize);
			inputBuffer.set(outputBuffer.array(), outputBuffer.arrayOffset(), outputBuffer.capacity());
			blockSize = bufferSize;
		} else {
			outputBuffer = new ByteList.Slice();
			blockSize = 0;
		}
	}

	public void write(int b) throws IOException {IOUtil.writeSingleByteHelper(this, b);}
	public void write(@NotNull byte[] b, int off, int len) throws IOException {
		if (out == null) throw new IOException("Stream closed");

		var ib = inputBuffer;
		try {
			if (blockSize == 0) {
				// 流模式直接处理
				cipher.crypt(ib.setR(b, off, len), ((ByteList.Slice) outputBuffer).set(b, off, len));
				out.write(b, off, len);
				return;
			}

			while (len > 0) {
				int writable = ib.writableBytes();
				int bytesToWrite = Math.min(len, writable);
				ib.put(b, off, bytesToWrite);

				off += bytesToWrite;
				len -= bytesToWrite;

				if (ib.writableBytes() == 0) flush();
			}
		} catch (Exception e) {
			IOUtil.closeSilently(this);
			Helpers.athrow(e);
		}
	}

	public void flush() throws IOException {
		if (blockSize == 0) return;

		outputBuffer.clear();
		try {
			cipher.crypt(inputBuffer, outputBuffer);
		} catch (GeneralSecurityException e) {
			Helpers.athrow(e);
		}
		outputBuffer.writeToStream(out);
		inputBuffer.compact();

		out.flush();
	}

	@Override
	public void finish() throws IOException {
		if (blockSize != 0 && inputBuffer.isReadable()) {
			flush();
			try {
				finalBlock();
			} catch (Exception e) {
				Helpers.athrow(e);
			}
		}

		IOUtil.closeSilently(outputBuffer);
		outputBuffer = ByteList.EMPTY;
		out.flush();
	}

	public synchronized void close() throws IOException {
		if (out == null) return;
		try {
			finish();
		} finally {
			IOUtil.closeSilently(out);
			out = null;
		}
	}

	protected void finalBlock() throws Exception {
		int blockSize = cipher.engineGetBlockSize()-1;
		if (blockSize > 0) {
			var ib = inputBuffer;
			int off = ib.wIndex();
			int end = 0 == (off&blockSize) ? off : (off|blockSize)+1;

			ib.wIndex(end);
			off += ib.arrayOffset();
			end += ib.arrayOffset();

			// zero padding
			byte[] buf = ib.array();
			//Arrays.fill(buf, off, end, (byte) 0);
			while (off < end) buf[off++] = 0;
		}

		// filter mode: may grab some byte and process, left remain unchanged
		outputBuffer.clear();
		cipher.cryptFinal(inputBuffer, outputBuffer);
		outputBuffer.writeToStream(out);

		if (inputBuffer.isReadable())
			throw new IOException("Remaining data after final block: "+inputBuffer.dump());
	}
}