package roj.image;

import roj.io.XDataInput;

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
	boolean init(XDataInput in) throws IOException;
	int getWidth();
	int getHeight();
	boolean hasAlpha();
	ColorSpace getColorSpace();
	ColorModel getColorModel();

	BufferedImage decodeImage(XDataInput in) throws IOException;
	void decodeImage(XDataInput in, IntConsumer argb) throws IOException;
}