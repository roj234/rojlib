package roj.image;

import roj.io.MyDataInput;

import java.awt.color.*;
import java.awt.image.*;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/5/19 0019 17:36
 */
public interface ImageDecoder {
	boolean init(MyDataInput in) throws IOException;
	int getWidth();
	int getHeight();
	boolean hasAlpha();
	ColorSpace getColorSpace();
	ColorModel getColorModel();

	BufferedImage decodeImage(MyDataInput in) throws IOException;
	void decodeImage(MyDataInput in, int[] argb) throws IOException;
}