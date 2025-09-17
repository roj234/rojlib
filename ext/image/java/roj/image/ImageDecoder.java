package roj.image;

import roj.io.ByteInput;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.util.function.IntConsumer;

/**
 * @author Roj234
 * @since 2024/5/19 17:36
 */
public interface ImageDecoder {
	boolean init(ByteInput in) throws IOException;
	int getWidth();
	int getHeight();
	boolean hasAlpha();
	ColorSpace getColorSpace();
	ColorModel getColorModel();

	BufferedImage decodeImage(ByteInput in) throws IOException;
	void decodeImage(ByteInput in, IntConsumer argb) throws IOException;
}