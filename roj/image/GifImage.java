package roj.image;

import roj.RequireUpgrade;
import roj.collect.IntList;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * GIF Image decoder
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GifImage {
	public void read(DynByteBuf r) throws IOException {
		String cl = r.readAscii(6);
		if (!cl.equals("GIF87a") && !cl.equals("GIF89a")) throw new IOException("Illegal header " + cl);

		// Block LSD
		width = r.readUShortLE();
		height = r.readUShortLE();
		wh = width * height;

		byte flag = r.readByte();
		sorted = isBitTrue(flag, 3);

		// 期待的显示器色深
		//int exceptedColorResolution = getSpecifyBit(flag, 4, 6);

		int colorTableSize = 0;
		if (isBitTrue(flag, 7)) { // globalColorTable flag
			colorTableSize = getBits(flag, 0, 2);
			colorTableSize = 1 << colorTableSize + 1;
		}

		bgColorIndex = r.readByte();

		int pct = r.readUnsignedByte();
		if (pct != 0) whPercent = (pct + 15.0d) / 64.0d;

		if (colorTableSize != 0) {
			// Block GCT
			readColors(globalColorTable = new int[colorTableSize], r);
		}

		while (r.isReadable()) {
			int type = r.readUnsignedByte();
			switch (type) {
				case 0x21: readExtension(r); break;
				case 0x2C: readDescriptor(r); break;
				case 0x3B: return;
				default: throw new IOException("Unknown blockId: " + type);
			}
		}
		throw new EOFException("Unexpected end of input.");
	}

	private static void readColors(int[] colorTable, DynByteBuf r) {
		int len = colorTable.length;
		for (int i = 0; i < len; i++) { 
			colorTable[i] = 0xFF000000 | r.readMedium();
		}
	}

	@RequireUpgrade
	private void readDescriptor(DynByteBuf r) {
		Frame f = new Frame();

		f.offsetX = r.readUShortLE();
		f.offsetY = r.readUShortLE();
		f.width = r.readUShortLE();
		f.height = r.readUShortLE();

		byte flag = r.readByte();
		f.sorted = isBitTrue(flag, 5);
		f.interlace = isBitTrue(flag, 6);
		if (isBitTrue(flag, 7)) {
			f.localColorTable = new int[1 << getBits(flag, 0, 2) + 1];
			readColors(f.localColorTable, r);
		}

		f.minCodeSize = r.readByte();

		int thisIndex = r.rIndex;
		int subBlockDataSize = skipSubBlock(r);
		r.rIndex = thisIndex;

		ByteList buf = new ByteList(subBlockDataSize + 2);
		skipSubBlock(r, buf);
		f.imgData = buf.toByteArray();

		endFrame();
	}

	private void readExtension(DynByteBuf r) throws IOException {
		int type = r.readUnsignedByte();
		switch (type) {
			case 0xFE: /* comment */ skipSubBlock(r); break;
			case 0xFF: readAppInfo(r); break;
			case 0x01: throw new UnsupportedOperationException("Text extension");
			case 0xF9:
				if (frame != null) throw new IOException("missing end tag");
				beginFrame();
				readGraphicController(r);
				break;
			default: throw new IOException("Unknown extension: "+type);
		}
	}

	private void readAppInfo(DynByteBuf r) {
		int len = r.readUnsignedByte();
		appName = r.readUTF(8);
		appCode = r.readUTF(3);
		skipSubBlock(r);
	}

	private static int skipSubBlock(DynByteBuf r) {
		int pos = r.rIndex;
		int len = r.readUnsignedByte();
		while (len != 0) {
			r.rIndex += len;
			len = r.readUnsignedByte();
			pos++;
		}
		return r.rIndex - pos - 1;
	}
	private static void skipSubBlock(DynByteBuf r, DynByteBuf buf) {
		int len = r.readUnsignedByte();
		while (len != 0) {
			buf.put(r.slice(len));
			len = r.readUnsignedByte();
		}
	}

	private void readGraphicController(DynByteBuf r) {
		Frame f = frame;
		r.rIndex++;
		byte flag = r.readByte();
		f.disposalMethod = (flag & 0x1C) >>> 2;
		f.transparent = isBitTrue(flag, 0);
		f.delay = r.readUShortLE();
		f.transparentColor = r.readByte();
		r.rIndex++;
	}

	public static boolean isBitTrue(int i, int bit) {
		return ((i >>> bit) & 1) != 0;
	}

	public static int getBits(int n, int fromInclude, int toInclude) {
		int k = 0;
		for (int i = fromInclude; i <= toInclude; i++) {
			k |= (1 << i);
		}
		return (n & k) >>> fromInclude;
	}

	static final class LZWInflater {
		public final IntList[] dict = new IntList[4096];
		public int length;

		public int minCodeSize;
		public int clearCode;
		public int endOfInfoCode;
		public int codeSize;

		public final void add(int last, int code) {
			IntList lastVal = dict[last];
			int nowVal = dict[code].get(0);
			IntList plus = dict[length++];
			plus.clear();
			plus.addAll(lastVal);
			plus.add(nowVal);
		}

		public int bitPos = 0;
		public byte[] byteData = null;

		public final int next() {
			int code = 0;
			for (int i = 0; i < codeSize; i++) {
				if ((byteData[bitPos >> 3] & (1 << (bitPos & 7))) != 0) {
					code |= 1 << i;
				}
				bitPos++;
			}
			return code;
		}

		public final void clear() {
			this.codeSize = minCodeSize + 1;
			this.length = clearCode + 2;
			for (int i = 0; i < clearCode; i++) {
				IntList d = dict[i];
				d.clear();
				d.add(i);
			}
			dict[clearCode].clear();
			dict[clearCode].add(0);
			dict[endOfInfoCode] = null;
		}

		public final void init(int minCodeSize, byte[] data) {
			this.minCodeSize = minCodeSize;
			this.clearCode = 1 << minCodeSize;
			this.endOfInfoCode = clearCode + 1;
			this.codeSize = minCodeSize + 1;
			this.byteData = data;
			this.bitPos = 0;
			this.length = 0;

			for (int i = 0; i < clearCode; i++) {
				IntList d = dict[i];
				if (d == null) {dict[i] = new IntList(4);} else d.clear();
			}
		}

		public String toString() {
			return "LZWDecoder: \n   Dict length: " + clearCode + ' ' + minCodeSize + "\n   Stream length: " + byteData.length;
		}
	}


	public int width;
	public int height;
	int wh;
	boolean sorted;
	boolean noPrevFrame = true;
	short bgColorIndex;
	int[] globalColorTable = ArrayCache.INTS;
	double whPercent = 1.0d;

	public String appName;
	public String appCode;

	Frame frame = null;
	public List<Frame> frames = new ArrayList<>(40);

	void beginFrame() {
		if (frame != null) return;//throw new RuntimeException("Frame is not end.");
		frame = new Frame();
		frames.add(frame);
	}

	void endFrame() {
		if (frame == null) throw new RuntimeException("Frame is not begin.");
		if (frame.disposalMethod == 3) noPrevFrame = false;
		frame = null;
	}

	public List<int[]> toColorArray() {
		List<int[]> frameAlignedData = new ArrayList<>((int) (frames.size() / 0.7) + 1);
		LZWInflater inf = new LZWInflater();
		int[] prevFrame = null;
		for (Frame frame : frames) {
			decodeGif(frame, inf);
			prevFrame = expandFrame(frame, prevFrame, frameAlignedData);
		}
		this.frame = null;
		//this.frames.clear();
		//this.frames = null;
		this.globalColorTable = ArrayCache.INTS;
		return frameAlignedData;
	}

	final void decodeGif(Frame frame, LZWInflater inf) {
		if (frame.pixels == null) {
			inf.init(frame.minCodeSize, frame.imgData);
			int[] color = frame.localColorTable == ArrayCache.INTS ? globalColorTable : frame.localColorTable;
			int[] pixels = imageDecode(frame, color, inf);
			if (frame.interlace) {
				pixels = deinterlace(pixels, frame);
			}
			frame.setImgData(pixels);
		} else {
			System.out.println("Warn: gif already decoded.");
		}
	}

	final void drawImage(int[] canvas, int[] frame, int offsetX, int offsetY, int frameWidth, int frameHeight, boolean doTransparent) {
		//System.out.println("frame size: " + frame.length);
		for (int canvasX = offsetX, frameX = 0, dx = offsetX + frameWidth; canvasX < dx; canvasX++, frameX++) {
			for (int canvasY = offsetY, frameY = 0, dy = offsetY + frameHeight; canvasY < dy; canvasY++, frameY++) {
				int px = frame[frameY * frameWidth + frameX];
				if (doTransparent && px == 0) continue;
				canvas[canvasY * width + canvasX] = px;
			}
		}
	}

	final void clearRect(int[] array, int offsetX, int offsetY, int frameWidth, int frameHeight) {
		for (int y = offsetY, dy = Math.min(this.height, offsetY + frameHeight); y < dy; y++) {
			for (int x = offsetX, dx = Math.min(this.width, offsetX + frameWidth); x < dx; x++) {
				array[y * width + x] = getBackgroundColor();
			}
		}
	}


	final int[] expandFrame(Frame f, int[] prev, List<int[]> list) {
		int[] next = new int[wh];
		if (f.disposalMethod == 3) {
			System.arraycopy(prev, 0, next, 0, this.wh);
		}

		if (prev == null) prev = new int[wh];
		drawImage(prev, f.pixels, f.offsetX, f.offsetY, f.width, f.height, f.transparent);
		list.add(prev);

		if (f.disposalMethod != 3) {
			System.arraycopy(prev, 0, next, 0, this.wh);
		}
		if (f.disposalMethod == 2) {
			clearRect(next, f.offsetX, f.offsetY, f.width, f.height);
		}
		return next;
	}

	final int[] imageDecode(Frame frame, int[] color, LZWInflater codes) {
		int clearCode = codes.clearCode;
		int endOfInfoCode = codes.endOfInfoCode;
		int transparent = frame.transparent ? frame.transparentColor & 0xFF : -1;
		IntList output = new IntList(clearCode);
		int code = 0;
		while (true) {
			int prevCode = code;
			code = codes.next();

			if (code == clearCode) {
				codes.clear();
				continue;
			} else if (code == endOfInfoCode) {
				break;
			}

			if (code < codes.length) {
				if (prevCode != clearCode) {
					codes.add(prevCode, code);
				}
			} else {
				if (code != codes.length) throw new ArrayIndexOutOfBoundsException("Invalid LZW code." + code + "/" + codes.length);
				codes.add(prevCode, prevCode);
			}
			for (int i : codes.dict[code]) {
				if (i == transparent) {output.add(0);} else output.add(color[i]);
			}

			if (codes.length == (1 << codes.codeSize) && codes.codeSize < 12) {
				// If we're at the last code and codeSize is 12, the next code will be a clearCode, and it'll be 12 bits long.
				codes.codeSize++;
			}
		}

		// I don't know if this is technically an error, but some GIFs do it.
		//if (Math.ceil(pos / 8) !== data.length) throw new Error('Extraneous LZW bytes.');
		//System.out.println(output.size);
		return output.toArray();
	}

	final int[] deinterlace(int[] src, Frame frame) {
		int w = frame.width, h = frame.height, wh = w * h;
		int[] dest = new int[src.length];

		int set2Y = h + 7 >>> 3;
		int set3Y = set2Y + (h + 3 >>> 3);
		int set4Y = set3Y + (h + 1 >>> 2);

		int set2 = w * set2Y, set3 = w * set3Y, set4 = w * set4Y;

		int w2 = w << 1, w4 = w2 << 1, w8 = w4 << 1;

		int from = 0, to = 0;
		for (; from < set2; from += w, to += w8) {
			System.arraycopy(src, from, dest, to, w);
		}
		for (to = w4; from < set3; from += w, to += w8) {
			System.arraycopy(src, from, dest, to, w);
		}
		for (to = w2; from < set4; from += w, to += w4) {
			System.arraycopy(src, from, dest, to, w);
		}
		for (to = w; from < wh; from += w, to += w2) {
			System.arraycopy(src, from, dest, to, w);
		}
		return dest;
	}

	public final int getBackgroundColor() {
		if (globalColorTable != ArrayCache.INTS) {
			return globalColorTable[bgColorIndex & 0xFF];
		}
		Frame frame = this.frames.get(0);
		if (frame.localColorTable != ArrayCache.INTS) return frame.localColorTable[bgColorIndex & 0xFF];
		return 0;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("GIF File X: \n      Width: ").append(width)
																		   .append("\n      Height: ")
																		   .append(height)
																		   .append("\n      Sorted: ")
																		   .append(sorted)
																		   .append("\n      Background: ")
																		   .append(getBackgroundColor())
																		   .append("\n      Application Name: ")
																		   .append(appName)
																		   .append("\n      Application (itself) identifier: ")
																		   .append(appCode)
																		   .append("\n");
		for (Frame frame : frames) {
			sb.append(frame).append('\n');
		}
		return sb.toString();
	}

	public static class Frame {
		public int offsetX;
		public int offsetY;
		public int width;
		public int height;

		int disposalMethod;
		boolean transparent;
		boolean sorted;
		boolean interlace;
		public int delay;
		int[] localColorTable;

		byte transparentColor;
		byte minCodeSize;
		byte[] imgData;

		public int[] pixels;

		public void setImgData(int[] pixels) {
			this.localColorTable = ArrayCache.INTS;
			this.pixels = pixels;
		}

		public String toString() {
			return "   Frame X: \n      Left: " + offsetX + "\n      Top: " + offsetY + "\n      Width: " + width + "\n      Height: " + height + "\n      Disposal method: " + disposalMethod + "\n      Transpant: " + transparent + "\n      Interlaced: " + interlace + "\n      Delay: " + delay + "\n      Decoded image ARGB data: " + imgData.length;
		}
	}
}