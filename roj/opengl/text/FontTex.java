package roj.opengl.text;

import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import roj.collect.CharMap;
import roj.collect.Int2IntMap;
import roj.collect.IntList;
import roj.collect.IntSet;
import roj.opengl.texture.TextureManager;
import roj.opengl.util.Util;
import roj.text.CharList;
import roj.util.DirectByteList;

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.nio.ByteBuffer;
import java.util.function.IntFunction;

import static org.lwjgl.opengl.GL11.*;

/**
 * @author Roj234
 * @since 2021/2/3 21:47
 */
public class FontTex {
	private static final Glyph NOT_DISPLAYABLE = new Glyph(0, new Rectangle(), 0, 0, 0);

	public static final class Glyph {
		public final int textureId;
		public int width, height;
		public float xOff, baseline;
		public final float u1, v1, u2, v2;

		public Glyph(int textureId, Rectangle rect, int basey, float x, float y) {
			this.textureId = textureId;
			this.width = rect.width;
			this.height = rect.height;
			this.baseline = rect.y + basey;
			this.u1 = x / TEXTURE_SIZE;
			this.v1 = y / TEXTURE_SIZE;
			this.u2 = (x + width) / TEXTURE_SIZE;
			this.v2 = (y + height) / TEXTURE_SIZE;
		}

		public void setWidth(int w1) {
			xOff = (w1 - width) / 2f;
		}

		@Override
		public String toString() {
			return "RenderedGlyph{" + "width=" + width + ", height=" + height + ", x=" + xOff + ", y=" + baseline + '}';
		}
	}

	private static final int TEXTURE_SIZE = 256;
	private static final int GLYPH_BORDER = 1;
	public static final Font DEFAULT_FONT;

	private static final IntFunction<IntList> WII = value -> new IntList();

	private static BufferedImage layoutImg;
	private static Graphics2D layoutGraphic;

	private static void resizeCharTmp(int w, int h) {
		if (layoutGraphic != null) layoutGraphic.dispose();

		layoutImg = new BufferedImage(w+2*GLYPH_BORDER, h+2*GLYPH_BORDER, BufferedImage.TYPE_BYTE_GRAY);

		Graphics2D cg = layoutGraphic = layoutImg.createGraphics();
		cg.setBackground(Color.BLACK);

		cg.setPaint(Color.WHITE);
	}

	static {
		DEFAULT_FONT = new Font(null, Font.PLAIN, 24);
		resizeCharTmp(1024, 24);
	}

	public Font font, fallback;

	private final BufferedImage texImage = new BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_BYTE_GRAY);
	private final Graphics2D texGraphic = texImage.createGraphics();

	private int textureId = -1;
	private final Int2IntMap textureIds = new Int2IntMap();

	private final CharMap<Glyph> glyphs = new CharMap<>();
	private final CharMap<IntList> byWidth = new CharMap<>();

	private final IntSet tmp0 = new IntSet();

	private int currImgX, currImgY, currLineHeight;
	public int maxLineHeight, baseline;
	public boolean antiAliasing;

	public FontTex(String nameAndCfg) {
		if (nameAndCfg != null) {
			this.font = Font.decode(nameAndCfg);
		} else {
			this.font = DEFAULT_FONT;
		}

		Rectangle2D bounds = font.getMaxCharBounds(initFRC());
		maxLineHeight = (int) Math.round(bounds.getHeight());
		baseline = (int) -Math.round(bounds.getY());

		texGraphic.setBackground(Color.BLACK);
	}

	public FontTex(String font, String fallback) {
		this(font);
		this.fallback = Font.decode(fallback);
	}

	public FontTex sameWidth() {
		// Ascii printable chars
		CharList tmp = new CharList();
		for (int i = 32; i < 127; i++) {
			if (i == ' ') continue;
			tmp.append((char) i);
		}
		return sameWidth(tmp);
	}
	public FontTex sameWidth(CharSequence tmp) {
		preRender(tmp,0, tmp.length());

		int max = 0;
		for (int i = 0; i < tmp.length(); i++) {
			max = Math.max(getCharWidth(tmp.charAt(i)), max);
		}
		for (int i = 0; i < tmp.length(); i++) {
			setCharWidth(tmp.charAt(i), max);
		}
		return this;
	}

	public FontTex antiAliasing(boolean b) {
		antiAliasing = b;
		return this;
	}

	public Glyph getEntry(char c) {
		Glyph tex = glyphs.get(c);
		if (tex != null) textureIds.getEntry(tex.textureId).v = (int) (System.currentTimeMillis()/1000);
		return tex;
	}

	public Glyph getOrCreateEntry(char c) {
		if (!glyphs.containsKey(c)) {
			if (0 == preRender(String.valueOf(c), 0, 1)) {
				glyphs.put(c, NOT_DISPLAYABLE);
			}
		}
		Glyph tex = glyphs.get(c);
		if (tex != null) textureIds.getEntry(tex.textureId).v = (int) (System.currentTimeMillis()/1000);
		return tex;
	}

	public IntList getEntriesByWidth(int w) {
		IntList list = byWidth.get((char) w);
		return list == null ? new IntList() : list;
	}

	public int getCharWidth(char c) {
		Glyph ft = getEntry(c);
		if (ft != null) return (int) (ft.width+ft.xOff);
		return font.createGlyphVector(initFRC(), new char[] {c}).getGlyphVisualBounds(0).getBounds().width;
	}

	public void setCharWidth(char c, int width) {
		Glyph entry = getOrCreateEntry(c);
		if (entry == null) throw new IllegalArgumentException("character '"+c+"' is not displayable in " + font);
		entry.setWidth(width);
		int newWidth = (int) (entry.width + entry.xOff);
		if (newWidth != entry.width) {
			byWidth.get((char) entry.width).remove(c);
			byWidth.computeIfAbsent((char) newWidth, WII).add(c);
		}
	}

	public Font getFont() {
		return font;
	}

	public void deleteGlResource() {
		if (textureIds.size() == 0) return;
		for (Int2IntMap.Entry entry : textureIds.selfEntrySet()) {
			glDeleteTextures(entry.getIntKey());
		}
		textureIds.clear();
		glyphs.clear();
		byWidth.clear();
		textureId = -1;
	}

	public void dumpTexture() {
		glDisable(GL_CULL_FACE);

		int z = 20;
		for (Int2IntMap.Entry entry : textureIds.selfEntrySet()) {
			glBindTexture(GL_TEXTURE_2D, entry.getIntKey());
			glBegin(GL_QUADS);
			glVertex3i(20, -20, z);
			glTexCoord2f(1, 1);
			glVertex3i(20, 20, z);
			glTexCoord2f(0, 1);
			glVertex3i(-20, 20, z);
			glTexCoord2f(0, 0);
			glVertex3i(-20, -20, z);
			glTexCoord2f(1, 0);
			glEnd();
			z += 20;
		}

		glEnable(GL_CULL_FACE);
	}

	public float averageGlyphPerTexture() {
		return glyphs.size() / (float)textureIds.size();
	}

	private final CharList clear = new CharList(), fail = new CharList();
	public int preRender(CharSequence chars, int start, int length) {
		length += start;
		CharMap<Glyph> done = glyphs;

		IntSet tmp = tmp0;
		tmp.clear();

		CharList clear = this.clear;clear.clear();
		CharList fail = this.fail;fail.clear();

		for (int i = start; i < length; i++) {
			char c = chars.charAt(i);
			if (!done.containsKey(c) && tmp.add(c)) {
				if (font.canDisplay(c)) {
					clear.append(c);
				} else {
					fail.append(c);
				}
			}
		}

		if (clear.length() > 0) {
			preRender0(clear.list, 0, clear.length());
		}
		if (fail.length() > 0 && fallback != null) {
			Font font1 = font;
			font = fallback;
			try {
				preRender0(fail.list, 0, fail.length());
			} finally {
				font = font1;
			}
		}

		return font.getSize();
	}

	private FontRenderContext initFRC() {
		Graphics2D cg = layoutGraphic;
		cg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasing ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		return cg.getFontRenderContext();
	}

	private void preRender0(char[] chars, int start, int length) {
		if (textureId == -1) {
			textureId = allocateTexture();
			currImgX = 0;
			currImgY = 0;
			currLineHeight = 0;
			texGraphic.clearRect(0,0,TEXTURE_SIZE,TEXTURE_SIZE);
		}

		GlyphVector vector = font.layoutGlyphVector(initFRC(), chars, start, start + length, 0);
		int flag = vector.getLayoutFlags();
		if ((flag & GlyphVector.FLAG_COMPLEX_GLYPHS) != 0) {
			System.err.println("[FontEx]Complex Char Glyph Detected");
		}

		int numGlyphs = vector.getNumGlyphs();

		for (int i = 1; i < numGlyphs; i++) {
			Point2D pos = vector.getGlyphPosition(i);
			pos.setLocation(pos.getX() + GLYPH_BORDER*vector.getGlyphCharIndex(i), pos.getY());
			vector.setGlyphPosition(i, pos);
		}

		Rectangle bound = vector.getPixelBounds(null, 0, 0);

		if (bound.width > layoutImg.getWidth() || bound.height > layoutImg.getHeight()) {
			resizeCharTmp(bound.width, bound.height);
		}

		layoutGraphic.clearRect(0, 0, bound.width+2*GLYPH_BORDER, bound.height+2*GLYPH_BORDER);
		layoutGraphic.drawGlyphVector(vector, -bound.x+GLYPH_BORDER, -bound.y+GLYPH_BORDER);

		int ascent = Math.abs(bound.y);
		int descent = bound.height - ascent;
		int baseY = baseline - ascent;

		Rectangle dirty = new Rectangle();

		int lineHeight = currLineHeight;
		int i, x = currImgX, y = currImgY;
		for (i = 0; i < numGlyphs; i++) {
			Rectangle rect = vector.getGlyphPixelBounds(i, null, -bound.x+GLYPH_BORDER, -bound.y+GLYPH_BORDER);

			if (x + rect.width + GLYPH_BORDER > TEXTURE_SIZE) {
				x = GLYPH_BORDER;
				y += lineHeight + GLYPH_BORDER;
				lineHeight = 0;
			}
			if (y + rect.height + GLYPH_BORDER > TEXTURE_SIZE) break;

			if (rect.height > lineHeight) lineHeight = rect.height;

			texGraphic.drawImage(layoutImg, x, y, x + rect.width, y + rect.height, rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, null);

			glyphs.put(chars[start + i], new Glyph(textureId, rect, baseY, x, y));

			byWidth.computeIfAbsent((char) rect.width, WII).add(chars[start + i]);

			rect.setLocation(x, y);
			dirty.add(rect);

			x += rect.width + GLYPH_BORDER;
		}

		updateTexture(dirty);

		if (i < numGlyphs) {
			// 超高, 搞一个新的材质
			textureId = -1;
			preRender0(chars, start + i, length - i);
		} else {
			currImgX = x;
			currImgY = y;
			currLineHeight = lineHeight;
		}
	}

	private void updateTexture(Rectangle dirty) {
		int prevTex = glGetInteger(GL_TEXTURE_BINDING_2D);
		Util.bindTexture(textureId);

		DirectByteList dbl = TextureManager.tryLockAndGetBuffer();
		// ! 向纹理中写入数据的时候不清空其内部的数据在特定情况可能会降低性能
		try {
			int a = TextureManager.copyImageToNative(dbl, texImage, dirty.x, dirty.y, dirty.width, dirty.height, 2);
			glTexSubImage2D(GL_TEXTURE_2D, 0, dirty.x, dirty.y, dirty.width, dirty.height, a >>> 16, a & 0xFFFF, dbl.nioBuffer());
		} finally {
			TextureManager.unlockAndReturnBuffer(dbl);
		}

		Util.bindTexture(prevTex);
	}

	private static void setFilter(int min, int max) {
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, min);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, max);
	}

	private int allocateTexture() {
		textureIds.put(textureId = glGenTextures(), (int) (System.currentTimeMillis()/1000));

		int prevTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
		Util.bindTexture(textureId);

		setFilter(GL_NEAREST_MIPMAP_LINEAR, GL_NEAREST);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);

		glTexParameteri(GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
		glTexParameteri(GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
		glTexParameteri(GL_TEXTURE_2D, GL14.GL_GENERATE_MIPMAP, GL_TRUE);

		glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA8, TEXTURE_SIZE, TEXTURE_SIZE, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

		Util.bindTexture(prevTexture);

		return textureId;
	}
}
