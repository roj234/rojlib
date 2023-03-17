package roj.opengl.texture;

import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import roj.io.IOUtil;
import roj.opengl.util.Util;
import roj.util.ByteList;
import roj.util.DirectByteList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.color.*;
import java.awt.image.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL11.*;

/**
 * 简易材质管理器
 *
 * @author Roj233
 * @since 2021/9/19 17:34
 */
public class TextureManager {
	public static final int TEXTURE_SIZE = 256;

/*	private final ToIntMap<String> textureIds = new ToIntMap<>();

	public void bindTexture(String id) {
		int i = textureIds.getOrDefault(id, -1);
		if (i == -1) {
			BufferedImage image = getTexture(id);
			if (image == null) {
				if (nullTextureId == -1) nullTextureId();
				i = nullTextureId;
			} else {
				i = glGenTextures();
				uploadTexture(image, i, 4);
			}
			textureIds.putInt(id, i);
		}
		glBindTexture(GL_TEXTURE_2D, i);
	}


	public void freeTexture(String id) {
		Integer i = textureIds.remove(id);
		if (i != null) {
			glDeleteTextures(i);
		}
	}

	@Override
	public void finalize() {
		if (textureIds.isEmpty()) return;
		ByteBuffer buffer = ByteBuffer.allocateDirect(4 * textureIds.size()).order(ByteOrder.nativeOrder());
		IntBuffer dst = buffer.asIntBuffer();
		for (ToIntMap.Entry<String> entry : textureIds.selfEntrySet()) {
			dst.put(entry.v);
		}
		glDeleteTextures(dst);
		NIOUtil.clean(buffer);
	}*/

	private static int nullTextureId = -1;
	public static int nullTextureId() {
		if (nullTextureId != -1) return nullTextureId;
		BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, 0x000000);
		img.setRGB(1, 0, 0xFF00FF);
		img.setRGB(0, 1, 0xFF00FF);
		img.setRGB(1, 1, 0x000000);
		int i = glGenTextures();
		uploadTexture(img, i, 4);
		return nullTextureId = i;
	}

	public static void uploadTexture(BufferedImage img, int textureId, int mipmap) {
		int prev = glGetInteger(GL_TEXTURE_BINDING_2D);
		Util.bindTexture(textureId);

		_glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, img.getWidth(), img.getHeight(), 0, img);

		if (mipmap > 0) {
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
			glTexParameteri(GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, mipmap);

			GL30.glGenerateMipmap(GL_TEXTURE_2D);
		}

		Util.bindTexture(prev);
	}

	public static void _glTexImage2D(int target, int level, int internalformat, int width, int height, int border, BufferedImage pixels) {
		DirectByteList dbl = tryLockAndGetBuffer();

		try {
			int a = copyImageToNative(dbl, pixels, 0);
			glTexImage2D(target, level, internalformat, width, height, border, a >>> 16, a, dbl.nioBuffer());
		} finally {
			unlockAndReturnBuffer(dbl);
		}
	}
	public static void _glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, BufferedImage pixels) {
		DirectByteList dbl = tryLockAndGetBuffer();

		try {
			int a = copyImageToNative(dbl, pixels, 0);
			glTexSubImage2D(target, level, xoffset, yoffset, width, height, a >>> 16, a, dbl.nioBuffer());
		} finally {
			unlockAndReturnBuffer(dbl);
		}
	}

	private static final AtomicReference<DirectByteList> UPLOADER = new AtomicReference<>(DirectByteList.allocateDirect(TEXTURE_SIZE*TEXTURE_SIZE*4));
	public static DirectByteList tryLockAndGetBuffer() {
		DirectByteList dbl = UPLOADER.getAndSet(null);
		if (dbl == null) dbl = DirectByteList.allocateDirect();
		dbl.clear();
		return dbl;
	}
	public static void unlockAndReturnBuffer(DirectByteList dbl) {
		int tries = 10;
		while (tries-- > 0) {
			DirectByteList cur = UPLOADER.get();
			if (cur == null || cur.capacity() < dbl.capacity()) {
				if (UPLOADER.compareAndSet(cur, dbl)) {
					dbl = null;
					break;
				}
			} else break;
		}
		if (dbl != null) dbl._free();
	}

	public static int copyImageToNative(DirectByteList nm, BufferedImage img, int type) {
		return copyImageToNative(nm, img, 0, 0, img.getWidth(), img.getHeight(), type);
	}
	public static int copyImageToNative(DirectByteList nm, BufferedImage img, int x, int y, int w, int h, int type) {
		nm.clear();
		int glDataType = 0;
		int glDataFormat = 0;

		Raster raster = img.getRaster();
		if (x == 0 && y == 0 && w == img.getWidth() && h == img.getHeight() && raster.getParent() == null) {
			switch (img.getType()) {
				case BufferedImage.TYPE_INT_RGB:
					fastCopy(img, nm, w*h*4);
					glDataType = GL_RGB;
					glDataFormat = GL_INT;
					break;
				case BufferedImage.TYPE_INT_ARGB_PRE:
				case BufferedImage.TYPE_INT_ARGB:
					fastCopy(img, nm, w*h*4);
					glDataType = GL12.GL_UNSIGNED_INT_8_8_8_8;
					glDataFormat = GL_UNSIGNED_INT;
					break;
				case BufferedImage.TYPE_INT_BGR:
					fastCopy(img, nm, w*h*4);
					glDataType = GL12.GL_UNSIGNED_INT_8_8_8_8_REV;
					glDataFormat = GL_UNSIGNED_INT;
					break;
				case BufferedImage.TYPE_3BYTE_BGR:
					fastCopy(img, nm, w*h*3);
					glDataType = GL12.GL_BGR;
					glDataFormat = GL_UNSIGNED_BYTE;
					break;
				case BufferedImage.TYPE_USHORT_565_RGB:
					fastCopy(img, nm, w*h*2);
					glDataType = GL12.GL_UNSIGNED_SHORT_5_6_5;
					glDataFormat = GL_UNSIGNED_SHORT;
					break;
				case BufferedImage.TYPE_USHORT_555_RGB:
					fastCopy(img, nm, w*h*2);
					glDataType = GL12.GL_UNSIGNED_SHORT_1_5_5_5_REV;
					glDataFormat = GL_UNSIGNED_SHORT;
					break;
				case BufferedImage.TYPE_BYTE_GRAY:
					fastCopy(img, nm, w*h);
					glDataType = GL_ALPHA;
					glDataFormat = GL_UNSIGNED_BYTE;
					break;
			}
		}

		if (glDataType == 0) {
			ColorModel cm = img.getColorModel();
			Object sb = null;

			// 字节序,??
			boolean LE = (type&2) != 0 && ByteOrder.LITTLE_ENDIAN == ByteOrder.nativeOrder();
			if ((type&1) == 0) {
				nm.ensureCapacity(w*h*4);
				glDataType = GL_RGBA;
				glDataFormat = GL_UNSIGNED_BYTE;

				for (int y1 = y; y1 < y+h; y1++) {
					for (int x1 = x; x1 < x+w; x1++) {
						sb = raster.getDataElements(x1, y1, sb);
						int rgb = cm.getRGB(sb); // argb
						rgb = (rgb << 8)|(rgb >>> 24);
						if(LE)nm.putIntLE(rgb);else nm.putInt(rgb);
					}
				}
			} else {
				nm.ensureCapacity(w*h*3);

				glDataType = GL_RGB;
				glDataFormat = GL_UNSIGNED_BYTE;

				for (int y1 = y; y1 < y+h; y1++) {
					for (int x1 = x; x1 < x+w; x1++) {
						sb = raster.getDataElements(x1, y1, sb);
						int rgb = cm.getRGB(sb);
						if(LE)nm.putMediumLE(rgb);else nm.putMedium(rgb);
					}
				}
			}
		}

		return (glDataType << 16) | glDataFormat;
	}
	private static void fastCopy(BufferedImage img, DirectByteList buf, int size) {
		buf.ensureCapacity(size); buf.wIndex(size);
		ByteBuffer up = buf.nioBuffer();
		DataBuffer db = img.getRaster().getDataBuffer();
		switch (db.getDataType()) {
			case DataBuffer.TYPE_BYTE: up.put(((DataBufferByte) db).getData()); break;
			case DataBuffer.TYPE_USHORT: up.asShortBuffer().put(((DataBufferUShort) db).getData());break;
			case DataBuffer.TYPE_SHORT: up.asShortBuffer().put(((DataBufferShort) db).getData());break;
			case DataBuffer.TYPE_INT: up.asIntBuffer().put(((DataBufferInt) db).getData());break;
		}
	}

	public static void stripOpaque(BufferedImage img) {
		WritableRaster raster = img.getRaster();
		Object sb = null;
		ColorModel cm = img.getColorModel();
		for (int y1 = 0; y1 < img.getHeight(); y1++) {
			for (int x1 = 0; x1 < img.getWidth(); x1++) {
				sb = raster.getDataElements(x1, y1, sb);
				int rgb = cm.getRGB(sb);
				if (((rgb & 0xFF000000) >>> 24) < 128) {
					sb = cm.getDataElements(0, sb);
					raster.setDataElements(x1, y1, sb);
				}
			}
		}
	}

	public BufferedImage readShared(InputStream in, boolean aprilFool) throws IOException {
		return readShared(in);
	}
	public BufferedImage readShared(InputStream in) throws IOException {
		PushbackInputStream pbi = new PushbackInputStream(in, 26);

		ByteList x = IOUtil.getSharedByteBuf();
		x.ensureCapacity(26);
		byte[] buf = x.list;
		pbi.unread(buf, 0, pbi.read(buf, 0, 26));

		int type = buf[25] & 0xFF;

		ImageInputStream iin = ImageIO.createImageInputStream(pbi);
		Iterator<ImageReader> readers = ImageIO.getImageReaders(iin);
		if (!readers.hasNext()) {
			iin.close();
			return new BufferedImage(0, 0, BufferedImage.TYPE_4BYTE_ABGR);
		}

		ImageReader r = readers.next();
		r.setInput(iin);

		try {
			return r.read(0, getParam(type, r));
		} finally {
			r.dispose();
			iin.close();
		}
	}

	private final BufferedImage[] images = new BufferedImage[7];
	private ImageReadParam getParam(int type, ImageReader r) throws IOException {
		int w = r.getWidth(0);
		int h = r.getHeight(0);

		BufferedImage dst = images[type];
		if (dst == null || dst.getWidth() < w || dst.getHeight() < h) {
			int it = getImageType(type);
			// including palette
			if (it == -1) return null;

			images[type] = dst = type == 4 ? createGrayAlpha(w, h) : new BufferedImage(w, h, it);
		}

		if (w < dst.getWidth() || h < dst.getHeight()) dst = dst.getSubimage(0, 0, w, h);

		ImageReadParam param = r.getDefaultReadParam();
		param.setDestination(dst);
		param.setDestinationBands(PNG_BANDS[type]);
		return param;
	}

	private static final int[][] PNG_BANDS = new int[][] {new int[] {0}, null, new int[] {0, 1, 2}, new int[] {0}, new int[] {0, 1}, null, new int[] {0, 1, 2, 3}};
	private static int getImageType(int type) {
		switch (type) {
			case 0: return BufferedImage.TYPE_BYTE_GRAY; // gray
			case 2: return BufferedImage.TYPE_3BYTE_BGR; // rgb
			case 4: return 0; // gray + alpha
			case 6: return BufferedImage.TYPE_4BYTE_ABGR; // rgb + alpha
		}
		return -1;
	}

	static {
		ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
		GRAY_ALPHA = new ComponentColorModel(cs, new int[] {8, 8}, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
	}
	private static final ColorModel GRAY_ALPHA;
	private static BufferedImage createGrayAlpha(int w, int h) {
		WritableRaster raster = GRAY_ALPHA.createCompatibleWritableRaster(w, h);
		return new BufferedImage(GRAY_ALPHA, raster, false, null);
	}
}
