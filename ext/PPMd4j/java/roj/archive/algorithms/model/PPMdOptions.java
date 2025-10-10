package roj.archive.algorithms.model;

import org.jetbrains.annotations.Range;
import roj.archive.algorithms.EntropyModelInputStream;
import roj.archive.algorithms.EntropyModelOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2025/10/20 02:30
 */
public final class PPMdOptions {
	private int order;
	private int dictSize;

	private static final byte[] ORDER_BY_LEVEL  = {4,4,5,5,5,6,6,7,8,9};
	private static final byte[] ORDER_BY_LEVEL2 = {3,4,4,4,5,6,6,7,8,9};

	public PPMdOptions() {this(0);}
	public PPMdOptions(@Range(from = 0, to = 9) int level) {
		if (level == 0) level = 5;

		level--;
		dictSize = (1 << 20) << level;
		order = ORDER_BY_LEVEL[level];
	}
	public PPMdOptions(@Range(from = PPMd7.PPMD7_MIN_ORDER, to = PPMd7.PPMD7_MAX_ORDER) int order,
					   @Range(from = PPMd7.PPMD7_MIN_MEM_SIZE, to = PPMd7.PPMD7_MAX_MEM_SIZE) int dictSize) {
		setOrder(order);
		setDictSize(dictSize);
	}

	public byte getOrder() { return (byte) order; }
	public int getDictSize() { return dictSize; }

	public PPMdOptions setOrder(@Range(from = PPMd7.PPMD7_MIN_ORDER, to = PPMd7.PPMD7_MAX_ORDER) int order) { this.order = order; return this; }
	public PPMdOptions setDictSize(@Range(from = PPMd7.PPMD7_MIN_MEM_SIZE, to = PPMd7.PPMD7_MAX_MEM_SIZE) int dictSize) { this.dictSize = dictSize; return this; }

	public PPMd7 createModel() {
		var model = new PPMd7();
		model.alloc(dictSize);
		model.init(order);
		return model;
	}

	public InputStream getInputStream(InputStream in) throws IOException {return new EntropyModelInputStream(in, createModel());}
	public OutputStream getOutputStream(OutputStream out) {return new EntropyModelOutputStream(out, createModel());}
}