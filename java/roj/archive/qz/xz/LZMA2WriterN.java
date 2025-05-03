package roj.archive.qz.xz;

import roj.io.Finishable;
import roj.io.IOUtil;
import roj.io.UnsafeOutputStream;
import roj.io.buf.BufferPool;
import roj.reflect.Unaligned;
import roj.util.ArrayCache;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;
import roj.util.NativeException;

import java.io.IOException;
import java.io.OutputStream;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2023/11/17 2:10
 */
class LZMA2WriterN extends OutputStream {
	static {initNatives();}
	private static long NATIVE_STRUCT_SIZE;

	private OutputStream out;

	public LZMA2WriterN(OutputStream out, LZMA2Options options) {
		if (out == null) throw new NullPointerException();
		this.out = out;
		try {
			setProps(options);
		} catch (IOException e) {
			assert false;
		}
	}

	public final void setProps(LZMA2Options opt) throws IOException {
		if (pCtx != 0) flush();

		byte[] dict = opt.getPresetDict();
		DynByteBuf nStruct = BufferPool.buffer(true, 128 + (dict != null ? dict.length : 0));

		//		typedef struct {
		//			u1 compressionLevel;
		//			u4 dictSize;
		//			void* presetDict;
		//			u4 presetDictLength;
		//			u1 lc, lp, pb;
		//			u1 mode, mf;
		//			u4 niceLen;
		//			u4 depthLimit;
		//			u1 async;
		//		} LZMA_OPTIONS_NATIVE;

		long addr = nStruct.address();
		U.putByte(addr, (byte) 6); // compressionLevel
		addr ++;
		U.putInt(addr, opt.getDictSize()); // dictSize
		addr += 4;

		if (dict != null) {
			U.putAddress(addr, nStruct.address()+128); // *presetDict
			addr += Unaligned.ADDRESS_SIZE;
			U.putInt(addr, dict.length); // presetDictLength
		} else {
			U.putAddress(addr, 0); // *presetDict
			addr += Unaligned.ADDRESS_SIZE;
			U.putInt(addr, 0); // presetDictLength
		}
		addr += 4;

		U.putByte(addr, (byte) opt.getLc()); // lc
		addr ++;
		U.putByte(addr, (byte) opt.getLp()); // lp
		addr ++;
		U.putByte(addr, (byte) opt.getPb()); // pb
		addr ++;
		U.putByte(addr, (byte) 0); // mode
		addr ++;
		U.putByte(addr, (byte) 0); // mf
		addr ++;
		U.putInt(addr, opt.getNiceLen()); // niceLen
		addr += 4;
		U.putInt(addr, opt.getDepthLimit()); // depthLimit
		addr += 4;
		U.putByte(addr, opt.getAsyncMan() == null ? 0 : (byte) opt.getAsyncMan().taskAffinity); // asyncThreads

		try {
			long l = nInit(nStruct.address());
		} finally {
			BufferPool.reserve(nStruct);
		}
	}

	@Override
	public final void write(int b) throws IOException {write(new byte[]{(byte) b}, 0, 1);}
	public final void write(byte[] buf, int off, int len) throws IOException {
		ArrayUtil.checkRange(buf, off, len);
		write0(buf, (long) Unaligned.ARRAY_BYTE_BASE_OFFSET+off, len);
	}
	public final void write(long off, int len) throws IOException { write0(null, off, len); }
	public final void write0(Object buf, long off, int len) throws IOException {
		try {
			while (len > 0) {
				int w = Math.min(inSize - inOffset, len);

				U.copyMemory(buf, off, null, pIn+NATIVE_STRUCT_SIZE+inOffset, w);

				long nWrite = nWrite(w);
				flush0();
				if (nWrite != 1 && nWrite != 0) throw new NativeException("Native FastLZMA return Error#"+nWrite);

				off += w;
				len -= w;
			}
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		}
	}

	public void flush() throws IOException {
		try {
			long i;
			do {
				i = nFlush();
				flush0();
			} while (i == 1);
			if (i != 0) throw new NativeException("Native FastLZMA return Error#"+i);
		} catch (Throwable e) {
			IOUtil.closeSilently(this);
			throw e;
		}
	}

	/**
	 * Finishes the stream but not closes the underlying OutputStream.
	 */
	public void finish() throws IOException {
		try {
			long i;
			do {
				i = nFinish();
				if (i == 0) outSize -= 4;
				flush0();
			} while (i == 1);
			if (i != 0) throw new NativeException("Native FastLZMA return Error#"+i);
		} catch (Throwable e) {
			IOUtil.closeSilently(out);
			throw e;
		} finally {
			nFree();
		}

		try {
			if (out instanceof Finishable f) f.finish();
		} catch (Throwable e) {
			IOUtil.closeSilently(out);
			throw e;
		}
	}

	/**
	 * Finishes the stream and closes the underlying OutputStream.
	 */
	public void close() throws IOException {
		try {
			if (pCtx != 0) finish();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if (out != null)
				out.close();
			out = null;
		}
	}

	private long pCtx, pIn, pOut;
	private int inOffset, inSize, outSize;
	private byte isFirstWrite = 1;

	private static native void initNatives();
	private static native long getMemoryUsage(long _nativeOptions);
	private synchronized native long nInit(long _nativeOptions);
	private synchronized native long nWrite(int len);
	private synchronized native long nFlush();
	private synchronized native long nFinish();
	private synchronized native void nFree();

	private synchronized void flush0() throws IOException {
		if (outSize == 0) return;
		writeMemory(out, pOut + NATIVE_STRUCT_SIZE + isFirstWrite, outSize - isFirstWrite);
		isFirstWrite = 0;
		outSize = 0;
	}

	private static void writeMemory(OutputStream out, long off, int len) throws IOException {
		if (out instanceof UnsafeOutputStream u) u.write0(null, off, len);
		else {
			byte[] arr = ArrayCache.getByteArray(Math.min(4096, len), false);
			while (len > 0) {
				int copyLen = Math.min(4096, len);
				U.copyMemory(null, off, arr, Unaligned.ARRAY_BYTE_BASE_OFFSET, copyLen);
				out.write(arr, 0, copyLen);

				len -= copyLen;
				off += copyLen;
			}
			ArrayCache.putArray(arr);
		}
	}
}