package roj.crypt;

import org.jetbrains.annotations.NotNull;
import roj.io.BufferPool;
import roj.io.Finishable;
import roj.io.IOUtil;
import roj.io.MBInputStream;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2022/11/12 15:27
 */
public class CipherInputStream extends MBInputStream implements Finishable {
	protected InputStream in;
	protected boolean eof;

	private final ByteList.Slice inputBuffer = new ByteList.Slice();
	private ByteList outputBuffer;

	protected final RCipher cipher;

	public CipherInputStream(InputStream in, RCipher cipher) {
		this.in = in;
		this.cipher = cipher;

		this.outputBuffer = (ByteList) BufferPool.buffer(false, Math.max(ArrayCache.IO_BUFFER_SIZE, cipher.engineGetBlockSize()));
		this.inputBuffer.set(outputBuffer.array(), outputBuffer.arrayOffset(), outputBuffer.capacity());
	}

	public void wipe() throws IOException {
		outputBuffer.clear();
		inputBuffer.clear();
	}

	@Override
	public int read(@NotNull byte[] b, int off, int len) throws IOException {
		int remaining = len;
		try {
			while (true) {
				int available = outputBuffer.readableBytes();

				// 如果缓冲区有足够数据
				if (available >= remaining) {
					outputBuffer.readFully(b, off, remaining);
					return len;
				}

				// 拷贝已处理的数据
				outputBuffer.readFully(b, off, available);

				off += available;
				remaining -= available;

				// 重置视图并压缩
				inputBuffer.compact();
				outputBuffer.clear();

				if (eof || inputBuffer.readStream(in, inputBuffer.writableBytes()) <= 0) {
					eof = true;

					// 没有剩余字节了
					if (!inputBuffer.isReadable()) {
						finish();
						return len == remaining ? -1 : len - remaining;
					}

					cipher.cryptFinal(inputBuffer, outputBuffer);
					if (inputBuffer.isReadable())
						throw new EOFException(cipher+"拒绝处理"+outputBuffer.readableBytes()+"大小的最终块");
				} else {
					cipher.crypt(inputBuffer, outputBuffer);
				}
			}
		} catch (Throwable e) {
			IOUtil.closeSilently(this);
			Helpers.athrow(e);
			return -1;
		}
	}

	@Override
	public int available() throws IOException {
		return outputBuffer.readableBytes() + (eof ? 0 : in.available());
	}

	@Override
	public void finish() throws IOException {
		// 释放缓冲区
		IOUtil.closeSilently(outputBuffer);
		outputBuffer = ByteList.EMPTY;
	}

	@Override
	public void close() throws IOException {
		finish();
		in.close();
	}
}