package roj.media.image;

import roj.io.CorruptedInputException;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;

import java.awt.color.*;
import java.awt.image.*;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2025/1/12 4:49
 */
public class QOI5551Encoder implements ImageEncoder {
	private final int[] hashTable = new int[64];

	@Override
	public void encodeImage(BufferedImage image, DynByteBuf out) throws IOException {
		ColorModel cm = image.getColorModel();
		ColorSpace cs = cm.getColorSpace();

		boolean sRGB = cs.isCS_sRGB();
		if (!sRGB) image = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB), null).filter(image, null);

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
			encode(argb, image.getWidth(), image.getHeight(), out);
		} finally {
			if (stored) ArrayCache.putArray(argb);
		}
	}

	@Override
	public void encode_sRGB(int[] argb, int width, int height, boolean hasAlpha, DynByteBuf out) throws IOException {encode(argb, width, height, out);}

	private void encode(int[] argb, int width, int height, DynByteBuf out) throws IOException {
		Arrays.fill(hashTable, 0);

		int pixels = width * height;
		if (argb.length < pixels) throw new CorruptedInputException("Pixel Array Too Small");

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

			if ((pixel >>> 24) != 0xFF) {
				out.put(0b11111111); // transparent
				prev = pixel;
				continue;
			}

			int r = (pixel >>> 19) & 31;
			int g = (pixel >>> 10) & 63;
			int b = (pixel >>>  3) & 31;

			int idx = (r * 3 + g * 5 + b * 7) & 63;

			/*checkBitmap:
			if (i + 8 < pixels) {
				int j = i;
				for (; i+j < pixels; j++) {
					if (argb[i + j] != pixel && argb[i + j] != prev)
						break;
				}
				if (j - i > 8) {
					System.out.println("BitMap "+(j-i));
					out.put(0b01000000);
				}
			}*/

			// Index encode  QOI_OP_INDEX
			if (hashTable[idx] == pixel) {
				out.put(idx);
			} else {
				hashTable[idx] = pixel;

				r -= (prev >>> 19) & 31;
				g -= (prev >>> 10) & 63;
				b -= (prev >>>  3) & 31;
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

					out.put(0b11111110).putShort(RGB888to565(pixel));
				}
			}


			prev = pixel;
		}
	}

	private static int RGB888to565(int pixel) {
		int r = (pixel >>> 19) & 31;
		int g = (pixel >>> 10) & 63;
		int b = (pixel >>>  3) & 31;
		return (r << 11) | (g << 5) | b;
	}
}