package roj.archive.algorithms.filter;

import roj.archive.algorithms.EntropyModelInputStream;
import roj.archive.algorithms.EntropyModelOutputStream;
import roj.archive.algorithms.model.PPMd7;
import roj.archive.algorithms.model.PPMdOptions;
import roj.archive.qz.QZCoder;
import roj.archive.qz.QZCustomCoder;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2025/10/20 02:35
 */
@QZCustomCoder
public final class PPMd extends QZCoder {
	public PPMd() { setOptions(new PPMdOptions()); }
	public PPMd(int level) { setOptions(new PPMdOptions(level)); }
	public PPMd(PPMdOptions options) { setOptions(options); }
	PPMd(boolean unused) {} // For factory

	private static final byte[] ID = { 3, 4, 1 };
	static {QZCoder.register(new PPMd(false));}

	public QZCoder factory() { return new PPMd(false); }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || PPMd.class != o.getClass()) return false;
		PPMd ppmd = (PPMd) o;
		return order == ppmd.order && dictSize == ppmd.dictSize;
	}

	@Override
	public int hashCode() {return 31 * order + dictSize;}

	public byte[] id() { return ID; }

	private byte order;
	private int dictSize;

	public PPMdOptions getOptions() {return new PPMdOptions(order, dictSize);}

	public void setOptions(PPMdOptions options) {
		order = options.getOrder();
		dictSize = options.getDictSize();
	}

	private PPMd7 createModel() {
		PPMd7 model = new PPMd7();
		model.alloc(dictSize);
		model.init(order);
		return model;
	}

	@Override
	public OutputStream encode(OutputStream out) {return new EntropyModelOutputStream(out, createModel());}

	@Override
	public InputStream decode(InputStream in, byte[] password, long uncompressedSize, AtomicInteger memoryLimit) throws IOException {
		useMemory(memoryLimit, getMemoryUsage(dictSize) >>> 10);
		return new EntropyModelInputStream(in, createModel());
	}

	@Override
	public String toString() {return "PPMD:o"+order+":mem"+(dictSize>>>20);}

	public void writeOptions(DynByteBuf buf) {buf.put(order).putIntLE(dictSize);}
	public void readOptions(DynByteBuf buf, int length) {
		order = buf.readByte();
		dictSize = buf.readIntLE();
	}

	// Memory estimation (call in decode; similar to LZMAInputStream.getMemoryUsage)
	private static int getMemoryUsage(int dictSize) {
		return Math.max(1 << 20, dictSize + (dictSize >> 4)); // ~dictSize + 6% overhead; min 1MB
	}
}