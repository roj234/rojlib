package roj.media.image;

import roj.io.CorruptedInputException;
import roj.io.MyDataInput;

import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/5/19 17:37
 */
public class QOIDecoder implements ImageDecoder {
	private final byte[] hashTable = new byte[64<<2];

	private int width, height;
	private boolean alpha, sRGB;

	@Override
	public boolean init(MyDataInput in) throws IOException {
		if (!in.readAscii(4).equals("qoif")) return false;
		width = in.readInt();
		height = in.readInt();

		int i = in.readUnsignedByte();
		if (i != 3) {
			if (i != 4) throw new CorruptedInputException("channels error: "+i);
			alpha = true;
		} else {
			alpha = false;
		}

		i = in.readUnsignedByte();
		if (i > 1) throw new CorruptedInputException("colorspace error: "+i);
		sRGB = i != 0;

		return true;
	}

	@Override
	public int getWidth() {return width;}
	@Override
	public int getHeight() {return height;}
	@Override
	public boolean hasAlpha() {return alpha;}

	@Override
	public ColorSpace getColorSpace() {return ColorSpace.getInstance(sRGB ? ColorSpace.CS_sRGB : ColorSpace.CS_LINEAR_RGB);}
	@Override
	public ColorModel getColorModel() {return new DirectColorModel(getColorSpace(), alpha ? 32 : 24, 0xff0000, 0xff00, 0xff, alpha ? 0xff000000 : 0, false, 3);}

	@Override
	public BufferedImage decodeImage(MyDataInput in) throws IOException {
		ColorModel cm = getColorModel();
		WritableRaster raster = cm.createCompatibleWritableRaster(width, height);

		int[] argb = ((DataBufferInt) raster.getDataBuffer()).getData();
		decodeImage(in, argb);

		return new BufferedImage(cm, raster, false, null);
	}

	@Override
	public void decodeImage(MyDataInput in, int[] argb) throws IOException {
		int i = 0;
		int pixels = width * height;
		if (argb.length < pixels) throw new CorruptedInputException("Pixel Array Too Small");

		int r = 0, g = 0, b = 0, a = 0xFF;
		byte[] tab = hashTable;

		outer:
		while (i < pixels) {
			noStore: {
				int b1 = in.readUnsignedByte();
				switch (b1 >>> 6) {
					case 0 -> { // INDEX
						b1 <<= 2;

						r = tab[b1  ] & 0xFF;
						g = tab[b1+1] & 0xFF;
						b = tab[b1+2] & 0xFF;
						a = tab[b1+3] & 0xFF;
						break noStore;
					}
					case 1 -> { // DIFF
						r += ((b1 >>> 4) & 3) - 2;
						g += ((b1 >>> 2) & 3) - 2;
						b +=  (b1        & 3) - 2;
					}
					case 2 -> { // LUMA
						int b2 = in.readUnsignedByte();
						int vg = (b1 & 0x3f) - 32;

						r += vg - 8 + ((b2 >>> 4) & 0x0f);
						g += vg;
						b += vg - 8 + (b2 & 0x0f);
					}
					case 3 -> {
						if (b1 >= 0b11111110) { // RGB
							r = in.readUnsignedByte();
							g = in.readUnsignedByte();
							b = in.readUnsignedByte();
							if (b1 == 0b11111111) { // RGBA
								a = in.readUnsignedByte();
							}
						} else { // RLE
							b1 &= 0x3f;
							int color = a << 24 | r << 16 | g << 8 | b;
							if (i+b1+1 >= pixels) throw new CorruptedInputException("RLE out of range");

							while (true) {
								argb[i++] = color;

								if (b1 == 0) continue outer;
								b1--;
							}
						}
					}
				}

				int h = ((r * 3 + g * 5 + b * 7 + a * 11) & 63) << 2;
				tab[h  ] = (byte) r;
				tab[h+1] = (byte) g;
				tab[h+2] = (byte) b;
				tab[h+3] = (byte) a;
			}

			argb[i++] = a << 24 | r << 16 | g << 8 | b;
		}
	}
}