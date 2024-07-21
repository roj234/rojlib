package roj.archive.qz.xz;

import org.intellij.lang.annotations.MagicConstant;
import roj.RojLib;
import roj.archive.qz.xz.lz.LZEncoder;
import roj.archive.qz.xz.lzma.LZMAEncoder;
import roj.concurrent.TaskHandler;
import roj.concurrent.TaskPool;
import roj.io.DummyOutputStream;
import roj.io.buf.BufferPool;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class LZMA2Options implements Cloneable {
	public static final int BEST_SPEED = 1, BEST_COMPRESSION = 9, DEFAULT_COMPRESSION = 5;

	public static final int DICT_SIZE_MIN = 4096;
	/**
	 * Maximum dictionary size for compression is 1535 MiB.
	 * <p>
	 * The decompressor supports bigger dictionaries, up to almost 2 GiB.
	 * With HC4 the encoder would support dictionaries bigger than 1535 MiB.
	 * The 1535 MiB limit comes from the current implementation of BT4 where
	 * we would otherwise hit the limits of signed ints in array indexing.
	 * <p>
	 * If you really need bigger dictionary for decompression,
	 * use {@link LZMA2InputStream} directly.
	 */
	public static final int DICT_SIZE_MAX = Integer.MAX_VALUE - 536879377;
	/**
	 * The default dictionary size is 8 MiB.
	 */
	public static final int DICT_SIZE_DEFAULT = 8 << 20;

	/**
	 * Maximum value for lc + lp is 4.
	 */
	public static final int LC_LP_MAX = 4;
	/**
	 * The default number of literal context bits is 3.
	 */
	public static final int LC_DEFAULT = 3;
	/**
	 * The default number of literal position bits is 0.
	 */
	public static final int LP_DEFAULT = 0;
	/**
	 * Maximum value for pb is 4.
	 */
	public static final int PB_MAX = 4;

	/**
	 * The default number of position bits is 2.
	 */
	public static final int PB_DEFAULT = 2;

	/**
	 * Compression mode: uncompressed.
	 * The data is wrapped into a LZMA2 stream without compression.
	 */
	public static final int MODE_UNCOMPRESSED = 0;
	/**
	 * Compression mode: fast.
	 * This is usually combined with a hash chain match finder.
	 */
	public static final int MODE_FAST = LZMAEncoder.MODE_FAST;
	/**
	 * Compression mode: normal.
	 * This is usually combined with a binary tree match finder.
	 */
	public static final int MODE_NORMAL = LZMAEncoder.MODE_NORMAL;

	/**
	 * Minimum value for <code>niceLen</code> is 8.
	 */
	public static final int NICE_LEN_MIN = 8;
	/**
	 * Maximum value for <code>niceLen</code> is 273.
	 */
	public static final int NICE_LEN_MAX = 273;

	/**
	 * Match finder: Hash Chain 2-3-4
	 */
	public static final int MF_HC4 = LZEncoder.MF_HC4;
	/**
	 * Match finder: Binary tree 2-3-4
	 */
	public static final int MF_BT4 = LZEncoder.MF_BT4;

	public static final int ASYNC_BLOCK_SIZE_MIN = 1 << 20, ASYNC_BLOCK_SIZE_MAX = 1 << 28;
	public static final byte ASYNC_DICT_NONE = 0, ASYNC_DICT_SET = 1, ASYNC_DICT_ASYNCSET = 2;

	private static final int[] presetToDictSize = {1 << 18, 1 << 20, 1 << 21, 1 << 22, 1 << 22, 1 << 23, 1 << 23, 1 << 24, 1 << 25, 1 << 26};
	private static final int[] presetToDepthLimit = {4, 8, 24, 48};

	private int dictSize;
	private byte[] presetDict = null;
	private int lc, lp, pb;
	private int mode;
	private int niceLen;
	private int mf;
	private int depthLimit;
	private boolean nativeAccelerate;

	private TaskHandler asyncExecutor;
	private BufferPool asyncBufferPool;
	private LZMA2Parallel asyncMan;

	public LZMA2Options() { this(DEFAULT_COMPRESSION); }
	public LZMA2Options(int preset) { setPreset(preset); }
	public LZMA2Options(int dictSize, int lc, int lp, int pb, int mode, int niceLen, int mf, int depthLimit) {
		setDictSize(dictSize)
			.setLcLp(lc, lp)
			.setPb(pb)
			.setMode(mode)
			.setNiceLen(niceLen)
			.setMatchFinder(mf)
			.setDepthLimit(depthLimit);
	}

	/**
	 * Sets the compression options to the given preset.
	 * <p>
	 * The presets 0-3 are fast presets with medium compression.
	 * The presets 4-6 are fairly slow presets with high compression.
	 * The default preset (<code>PRESET_DEFAULT</code>) is 6.
	 * <p>
	 * The presets 7-9 are like the preset 6 but use bigger dictionaries
	 * and have higher compressor and decompressor memory requirements.
	 * Unless the uncompressed size of the file exceeds 8&nbsp;MiB,
	 * 16&nbsp;MiB, or 32&nbsp;MiB, it is waste of memory to use the
	 * presets 7, 8, or 9, respectively.
	 */
	public void setPreset(int preset) {
		if (preset < 0 || preset > 9) throw new IllegalArgumentException("Unsupported preset: " + preset);

		lc = LC_DEFAULT;
		lp = LP_DEFAULT;
		pb = PB_DEFAULT;
		dictSize = presetToDictSize[preset];

		if (preset <= 3) {
			mode = MODE_FAST;
			mf = MF_HC4;
			niceLen = preset <= 1 ? 128 : NICE_LEN_MAX;
			depthLimit = presetToDepthLimit[preset];
		} else {
			mode = MODE_NORMAL;
			mf = MF_BT4;
			niceLen = (preset == 4) ? 16 : (preset == 5) ? 32 : 64;
			depthLimit = 0;
		}
	}

	/**
	 * Sets the dictionary size in bytes.
	 * <p>
	 * The dictionary (or history buffer) holds the most recently seen
	 * uncompressed data. Bigger dictionary usually means better compression.
	 * However, using a dictionary bigger than the size of the uncompressed
	 * data is waste of memory.
	 * <p>
	 * Any value in the range [DICT_SIZE_MIN, DICT_SIZE_MAX] is valid,
	 * but sizes of 2^n and 2^n&nbsp;+&nbsp;2^(n-1) bytes are somewhat
	 * recommended.
	 *
	 * @throws IllegalArgumentException <code>dictSize</code> is not supported
	 */
	public LZMA2Options setDictSize(int dictSize) {
		if (dictSize < DICT_SIZE_MIN) throw new IllegalArgumentException("LZMA2 dictionary size must be at least 4 KiB: " + dictSize + " B");
		if (dictSize > DICT_SIZE_MAX) throw new IllegalArgumentException("LZMA2 dictionary size must not exceed " + (DICT_SIZE_MAX >> 20) + " MiB: " + dictSize + " B");

		this.dictSize = dictSize;
		return this;
	}
	public int getDictSize() { return dictSize; }

	/**
	 * Sets a preset dictionary. Use null to disable the use of
	 * a preset dictionary. By default there is no preset dictionary.
	 * <p>
	 * <b>The .xz format doesn't support a preset dictionary for now.
	 * Do not set a preset dictionary unless you use raw LZMA2.</b>
	 * <p>
	 * Preset dictionary can be useful when compressing many similar,
	 * relatively small chunks of data independently from each other.
	 * A preset dictionary should contain typical strings that occur in
	 * the files being compressed. The most probable strings should be
	 * near the end of the preset dictionary. The preset dictionary used
	 * for compression is also needed for decompression.
	 */
	public LZMA2Options setPresetDict(byte[] presetDict) {
		this.presetDict = presetDict;
		return this;
	}
	public byte[] getPresetDict() { return presetDict; }

	/**
	 * Sets the number of literal context bits and literal position bits.
	 * <p>
	 * <code>Lp</code> affects what kind of alignment in the uncompressed data is
	 * assumed when encoding literals. See {@link #setPb(int) setPb} for
	 * more information about alignment.
	 * <p>
	 * <p> Lc:
	 * All bytes that cannot be encoded as matches are encoded as literals.
	 * That is, literals are simply 8-bit bytes that are encoded one at
	 * a time.
	 * <p>
	 * The literal coding makes an assumption that the highest <code>lc</code>
	 * bits of the previous uncompressed byte correlate with the next byte.
	 * For example, in typical English text, an upper-case letter is often
	 * followed by a lower-case letter, and a lower-case letter is usually
	 * followed by another lower-case letter. In the US-ASCII character set,
	 * the highest three bits are 010 for upper-case letters and 011 for
	 * lower-case letters. When <code>lc</code> is at least 3, the literal
	 * coding can take advantage of this property in the  uncompressed data.
	 * <p>
	 * The default value (3) is usually good. If you want maximum compression,
	 * try <code>setLc(4)</code>. Sometimes it helps a little, and sometimes it
	 * makes compression worse. If it makes it worse, test for example
	 * <code>setLc(2)</code> too.
	 * <p>
	 * <p>
	 * The sum of <code>lc</code> and <code>lp</code> is limited to 4.
	 * Trying to exceed it will throw an exception. This function lets
	 * you change both at the same time.
	 *
	 * @throws IllegalArgumentException <code>lc</code> and <code>lp</code>
	 * are invalid
	 */
	public LZMA2Options setLcLp(int lc, int lp) {
		if ((lc|lp) < 0 || lc + lp > LC_LP_MAX)
			throw new IllegalArgumentException("lc + lp must not exceed " + LC_LP_MAX + ": " + lc + " + " + lp);

		this.lc = lc;
		this.lp = lp;
		return this;
	}

	/**
	 * Sets the number of position bits.
	 * <p>
	 * This affects what kind of alignment in the uncompressed data is
	 * assumed in general. The default (2) means four-byte alignment
	 * (2^<code>pb</code> = 2^2 = 4), which is often a good choice when
	 * there's no better guess.
	 * <p>
	 * When the alignment is known, setting the number of position bits
	 * accordingly may reduce the file size a little. For example with text
	 * files having one-byte alignment (US-ASCII, ISO-8859-*, UTF-8), using
	 * <code>setPb(0)</code> can improve compression slightly. For UTF-16
	 * text, <code>setPb(1)</code> is a good choice. If the alignment is
	 * an odd number like 3 bytes, <code>setPb(0)</code> might be the best
	 * choice.
	 * <p>
	 * Even though the assumed alignment can be adjusted with
	 * <code>setPb</code> and <code>setLp</code>, LZMA2 still slightly favors
	 * 16-byte alignment. It might be worth taking into account when designing
	 * file formats that are likely to be often compressed with LZMA2.
	 *
	 * @throws IllegalArgumentException <code>pb</code> is invalid
	 */
	public LZMA2Options setPb(int pb) {
		if (pb < 0 || pb > PB_MAX) throw new IllegalArgumentException("pb must not exceed " + PB_MAX + ": " + pb);

		this.pb = pb;
		return this;
	}

	public int getLc() { return lc; }
	public int getLp() { return lp; }
	public int getPb() { return pb; }

	public byte getPropByte() { return (byte) ((pb * 5 + lp) * 9 + lc); }
	public LZMA2Options setPropByte(int propByte) {
		int props = propByte & 0xFF;
		if (props > (4 * 5 + 4) * 9 + 8) throw new IllegalArgumentException("Invalid LZMA properties byte");

		pb = props / (9 * 5);
		props -= pb * 9 * 5;
		lp = props / 9;
		lc = props - lp * 9;
		return this;
	}

	/**
	 * Sets the compression mode.
	 * <p>
	 * This specifies the method to analyze the data produced by
	 * a match finder. The default is <code>MODE_FAST</code> for presets
	 * 0-3 and <code>MODE_NORMAL</code> for presets 4-9.
	 * <p>
	 * Usually <code>MODE_FAST</code> is used with Hash Chain match finders
	 * and <code>MODE_NORMAL</code> with Binary Tree match finders. This is
	 * also what the presets do.
	 * <p>
	 * The special mode <code>MODE_UNCOMPRESSED</code> doesn't try to
	 * compress the data at all (and doesn't use a match finder) and will
	 * simply wrap it in uncompressed LZMA2 chunks.
	 *
	 * @throws IllegalArgumentException <code>mode</code> is not supported
	 */
	public LZMA2Options setMode(int mode) {
		if (mode < MODE_UNCOMPRESSED || mode > MODE_NORMAL) throw new IllegalArgumentException("Unsupported compression mode: " + mode);

		this.mode = mode;
		return this;
	}
	public int getMode() { return mode; }

	/**
	 * Sets the nice length of matches.
	 * Once a match of at least <code>niceLen</code> bytes is found,
	 * the algorithm stops looking for better matches. Higher values tend
	 * to give better compression at the expense of speed. The default
	 * depends on the preset.
	 *
	 * @throws IllegalArgumentException <code>niceLen</code> is invalid
	 */
	public LZMA2Options setNiceLen(int niceLen) {
		if (niceLen < NICE_LEN_MIN) throw new IllegalArgumentException("Minimum nice length of matches is " + NICE_LEN_MIN + " bytes: " + niceLen);
		if (niceLen > NICE_LEN_MAX) throw new IllegalArgumentException("Maximum nice length of matches is " + NICE_LEN_MAX + ": " + niceLen);

		this.niceLen = niceLen;
		return this;
	}
	public int getNiceLen() { return niceLen; }

	/**
	 * Sets the match finder type.
	 * <p>
	 * Match finder has a major effect on compression speed, memory usage,
	 * and compression ratio. Usually Hash Chain match finders are faster
	 * than Binary Tree match finders. The default depends on the preset:
	 * 0-3 use <code>MF_HC4</code> and 4-9 use <code>MF_BT4</code>.
	 *
	 * @throws IllegalArgumentException <code>mf</code> is not supported
	 */
	public LZMA2Options setMatchFinder(int mf) {
		if (mf != MF_HC4 && mf != MF_BT4) throw new IllegalArgumentException("Unsupported match finder: " + mf);
		this.mf = mf;
		return this;
	}
	public int getMatchFinder() { return mf; }

	/**
	 * Sets the match finder search depth limit.
	 * <p>
	 * The default is a special value of <code>0</code> which indicates that
	 * the depth limit should be automatically calculated by the selected
	 * match finder from the nice length of matches.
	 * <p>
	 * Reasonable depth limit for Hash Chain match finders is 4-100 and
	 * 16-1000 for Binary Tree match finders. Using very high values can
	 * make the compressor extremely slow with some files. Avoid settings
	 * higher than 1000 unless you are prepared to interrupt the compression
	 * in case it is taking far too long.
	 *
	 * @throws IllegalArgumentException <code>depthLimit</code> is invalid
	 */
	public LZMA2Options setDepthLimit(int depthLimit) {
		if (depthLimit < 0) throw new IllegalArgumentException("Depth limit cannot be negative: " + depthLimit);
		this.depthLimit = depthLimit;
		return this;
	}
	public int getDepthLimit() { return depthLimit; }

	/**
	 * <pre>启用对于单独压缩流的多线程压缩模式
	 * <b>注意，对比{@link roj.archive.qz.QZFileWriter#parallel()}的不同文件并行模式,单压缩流并行会损失千分之一左右压缩率</b>
	 * @param blockSize 任务按照该大小分块并行，设置为-1来自动选择(不推荐自动选择)
	 * @param executor 线程池
	 * @param affinity 最大并行任务数量 (1-255)
	 * @param dictMode 任务的词典处理模式，设置为-1来自动选择(不推荐自动选择)
	 * <pre>{@link #ASYNC_DICT_NONE} 每个块重置词典, 速度快, 压缩率差, 内存占用小 (7-zip的默认模式) (支持并行解压)
	 * {@link #ASYNC_DICT_SET} 在write的调用线程上设置词典, 速度慢, 压缩率好, 内存占用中等
	 * {@link #ASYNC_DICT_ASYNCSET} 在异步任务线程上设置词典, 速度中等, 压缩率好, 内存大
	 */
	public void setAsyncMode(int blockSize, TaskHandler executor, int affinity, BufferPool bufferPool, @MagicConstant(intValues = {ASYNC_DICT_NONE,ASYNC_DICT_SET,ASYNC_DICT_ASYNCSET}) int dictMode) {
		asyncExecutor = executor;
		asyncBufferPool = bufferPool;
		asyncMan = new LZMA2Parallel(this, blockSize, dictMode, affinity);
	}
	public void setAsyncMode(TaskHandler executor, BufferPool bufferPool, LZMA2Parallel parallel) {
		asyncExecutor = executor;
		asyncBufferPool = bufferPool;
		asyncMan = parallel;
	}
	public void clearAsyncMode() {
		asyncExecutor = null;
		asyncBufferPool = null;
		asyncMan = null;
	}
	public TaskHandler getAsyncExecutor() { return asyncExecutor; }
	public BufferPool getAsyncBufferPool() { return asyncBufferPool; }
	public LZMA2Parallel getAsyncMan() { return asyncMan; }

	public void setNativeAccelerate(boolean nativeAccelerate) {this.nativeAccelerate = nativeAccelerate;}
	public boolean isNativeAccelerate() {return nativeAccelerate;}

	public int getEncoderMemoryUsage() { return mode == MODE_UNCOMPRESSED ? LZMA2StoredWriter.getMemoryUsage() : LZMA2Writer.getMemoryUsage(this); }
	public OutputStream getOutputStream(OutputStream out) {
		if (mode == MODE_UNCOMPRESSED) return new LZMA2StoredWriter(out);
		if (nativeAccelerate && RojLib.hasNative(RojLib.FAST_LZMA)) return new LZMA2WriterN(out, this);
		return asyncMan != null ? asyncMan.createEncoder(out) : new LZMA2Writer(out, this);
	}

	public int getDecoderMemoryUsage() { return LZMA2InputStream.getMemoryUsage(dictSize); }
	public InputStream getInputStream(InputStream in) throws IOException { return new LZMA2InputStream(in, dictSize, presetDict); }

	public LZMA2Options clone() {
		try {
			LZMA2Options opt = (LZMA2Options) super.clone();
			opt.clearAsyncMode();
			return opt;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	public int findBestProps(byte[] data) { return findBestProps(data, TaskPool.Common()); }
	public int findBestProps(byte[] data, TaskPool th) {
		AtomicReference<Object[]> ref = new AtomicReference<>();
		for (int lc = 0; lc <= 4; lc++) {
			for (int lp = 0; lp <= 4-lc; lp++) {
				for (int pb = 0; pb <= 4; pb++) {
					LZMA2Options copy = clone();
					copy.dictSize = Math.max(Math.min(copy.dictSize, data.length), 4096);
					copy.lc = lc;
					copy.lp = lp;
					copy.pb = pb;

					th.submit(() -> {
						DummyOutputStream counter = new DummyOutputStream();
						try (OutputStream os = copy.getOutputStream(counter)) {
							os.write(data);
						} catch (Exception ignored) {}

						Object[] b = new Object[]{copy, counter.wrote};
						while (true) {
							Object[] prev = ref.get();
							if (prev != null && (int)prev[1] <= counter.wrote) return;
							if (ref.compareAndSet(prev, b)) return;
						}
					});
				}
			}
		}

		th.awaitFinish();
		Object[] min = ref.get();
		LZMA2Options best = (LZMA2Options) min[0];
		this.lc = best.lc;
		this.lp = best.lp;
		this.pb = best.pb;
		return (int)min[1];
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || LZMA2Options.class != o.getClass()) return false;
		LZMA2Options options = (LZMA2Options) o;

		if (dictSize != options.dictSize) return false;
		if (lc != options.lc) return false;
		if (lp != options.lp) return false;
		if (pb != options.pb) return false;
		if (mode != options.mode) return false;
		if (niceLen != options.niceLen) return false;
		if (mf != options.mf) return false;
		if (depthLimit != options.depthLimit) return false;
		if (!Arrays.equals(presetDict, options.presetDict)) return false;
		return asyncMan != null ? asyncMan.equals(options.asyncMan) : options.asyncMan == null;
	}

	@Override
	public int hashCode() {
		int result = dictSize;
		result = 31 * result + Arrays.hashCode(presetDict);
		result = 31 * result + lc;
		result = 31 * result + lp;
		result = 31 * result + pb;
		result = 31 * result + mode;
		result = 31 * result + niceLen;
		result = 31 * result + mf;
		result = 31 * result + depthLimit;
		return result;
	}

	@Override
	public String toString() {
		CharList sb = new CharList();

		if (MathUtils.getMin2PowerOf(dictSize) == dictSize) sb.append(31-Integer.numberOfLeadingZeros(dictSize));
		else sb.append(TextUtil.scaledNumber(dictSize));

		if (lc != LC_DEFAULT) sb.append(":lc").append(lc);
		if (lp != LP_DEFAULT) sb.append(":lp").append(lp);
		if (pb != PB_DEFAULT) sb.append(":pb").append(pb);

		if (mode == MODE_FAST) sb.append(" FAST");
		else if (mode == MODE_UNCOMPRESSED) sb.append(" STORE");

		return sb.toStringAndFree();
	}
}