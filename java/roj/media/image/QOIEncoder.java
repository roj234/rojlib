package roj.media.image;

import roj.io.CorruptedInputException;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;

import java.awt.color.*;
import java.awt.image.*;
import java.io.IOException;
import java.util.Arrays;

/**
 * See <a href="https://qoiformat.org/">The Quite OK Image Format</a> for details
 * @author Roj234
 * @since 2024/5/19 0019 16:59
 */
public class QOIEncoder implements ImageEncoder {
	private final int[] hashTable = new int[64];

	@Override
	public void encodeImage(BufferedImage image, DynByteBuf out) throws IOException {
		ColorModel cm = image.getColorModel();
		ColorSpace cs = cm.getColorSpace();

		boolean sRGB = cs.isCS_sRGB();
		if (!sRGB) {
			if (cs != ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB)) {
				image = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB), null).filter(image, null);
				cm = image.getColorModel();
				sRGB = true;
			}
		}

		WritableRaster raster = image.getRaster();
		boolean stored;
		int[] argb;
		switch (image.getType()) {
			case BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_RGB -> {
				stored = false;
				argb = ((DataBufferInt) raster.getDataBuffer()).getData();
			}
			/*case BufferedImage.TYPE_INT_BGR -> ;
			case BufferedImage.TYPE_3BYTE_BGR -> ;
			case BufferedImage.TYPE_4BYTE_ABGR -> ;*/
			default -> {
				stored = true;
				argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), ArrayCache.getIntArray(image.getWidth()*image.getHeight(), 0), 0, image.getWidth());
			}
		}

		try {
			encode(argb, image.getWidth(), image.getHeight(), cm.hasAlpha(), sRGB, out);
		} finally {
			if (stored) ArrayCache.putArray(argb);
		}
	}

	@Override
	public void encode_sRGB(int[] argb, int width, int height, boolean hasAlpha, DynByteBuf out) throws IOException {encode(argb, width, height, hasAlpha, true, out);}

	private void encode(int[] argb, int width, int height, boolean alpha, boolean sRGB, DynByteBuf out) throws IOException {
		Arrays.fill(hashTable, 0);

		// uint8_t channels;
		// 3 = RGB, 4 = RGBA

		// uint8_t colorspace;
		// 0 = sRGB with linear alpha
		// 1 = all channels linear

		out.putAscii("qoif").putInt(width).putInt(height).put(alpha ? 4 : 3).put(sRGB ? 0 : 1);

		int pixels = width * height;
		if (argb.length < pixels) throw new CorruptedInputException("Pixel Array Too Small");

		if (alpha) encodeARGB(argb, out, pixels);
		else encodeRGB(argb, out, pixels);

		out.putLong(1);
	}

	private void encodeARGB(int[] argb, DynByteBuf out, int pixels) {
		int prev = 0xFF000000;
		for (int i = 0; i < pixels;) {
			int pixel = argb[i++];

			// Run length encode  QOI_OP_RUN
			if (pixel == prev) {
				int run = 0;
				while (argb[i] == pixel && run < 61) {
					run++;
					if (++i == pixels) break;
				}

				out.put(0b11000000 | run);
				continue;
			}

			int a = pixel >>> 24;
			int r = (pixel >>> 16) & 0xFF;
			int g = (pixel >>> 8) & 0xFF;
			int b = pixel & 0xFF;

			int idx = (r * 3 + g * 5 + b * 7 + a * 11) & 63;
			// Index encode  QOI_OP_INDEX
			if (hashTable[idx] == pixel) {
				out.put(idx);

				prev = pixel;
				continue;
			}

			hashTable[idx] = pixel;

			// Full RGBA  QOI_OP_RGBA
			if (prev >>> 24 != a) {
				out.put(0b11111111).putInt(pixel);

				prev = pixel;
				continue;
			}

			r -= (prev >>> 16) & 0xFF;
			g -= (prev >>> 8) & 0xFF;
			b -= prev & 0xFF;
			searcher:
			if ((((r+2)|(g+2)|(b+2)) & ~3) == 0) { // RGB delta in [-2,1]  QOI_OP_DIFF
				out.put(0b01000000 | (r + 2) << 4 | (g + 2) << 2 | (b + 2));
			} else {
				if (/*((g+32) & ~63) != 0*/g > -33 && g < 32) { // G delta in [-32, 31]  QOI_OP_LUMA
					int vg_r = r - g;
					int vg_b = b - g;

					// in [-8, 7]
					if ((((vg_r+8)|(vg_b+8)) & ~15) == 0) {
						out.put(0b10000000 | (g+32)).put((vg_r + 8) << 4 | (vg_b + 8));
						break searcher;
					}
				}

				out.put(0b11111110).putMedium(pixel);
			}

			prev = pixel;
		}
	}
	private void encodeRGB(int[] rgb, DynByteBuf out, int pixels) {
		int prev = 0;
		for (int i = 0; i < pixels;) {
			int pixel = rgb[i++];

			// Run length encode  QOI_OP_RUN
			if (pixel == prev) {
				int run = 0;
				while (rgb[i] == pixel && run < 62) {
					i++;
					run++;
				}

				out.put(0b11000000 | run);
				continue;
			}

			int r = (pixel >>> 16) & 0xFF;
			int g = (pixel >>> 8) & 0xFF;
			int b = pixel & 0xFF;

			int idx = (r * 3 + g * 5 + b * 7 + 2805) & 63;
			// Index encode  QOI_OP_INDEX
			if (hashTable[idx] == pixel) {
				out.put(idx);

				prev = pixel;
				continue;
			}

			hashTable[idx] = pixel;

			r -= (prev >>> 16) & 0xFF;
			g -= (prev >>> 8) & 0xFF;
			b -= prev & 0xFF;
			searcher:
			if ((((r+2)|(g+2)|(b+2)) & ~3) == 0) {
				out.put(0b01000000 | (r + 2) << 4 | (g + 2) << 2 | (b + 2));
			} else {
				if (g > -33 && g < 32) {
					int vg_r = r - g;
					int vg_b = b - g;

					if ((((vg_r+8)|(vg_b+8)) & ~15) == 0) {
						out.put(0b10000000 | (g+32)).put((vg_r + 8) << 4 | (vg_b + 8));
						break searcher;
					}
				}

				out.put(0b11111110).putMedium(pixel);
			}

			prev = pixel;
		}
	}
}