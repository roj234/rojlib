package roj.image;

import roj.util.DynByteBuf;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/5/19 17:00
 */
public interface ImageEncoder {
	void encodeImage(BufferedImage image, DynByteBuf out) throws IOException;
	void encode_sRGB(int[] rgba, int width, int height, boolean hasAlpha, DynByteBuf out) throws IOException;
}