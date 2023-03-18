package roj.opengl.text;

import org.lwjgl.opengl.GL11;
import roj.collect.IntList;
import roj.collect.MyBitSet;
import roj.opengl.text.FontTex.Glyph;
import roj.opengl.util.Util;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;
import roj.text.TextUtil;

import java.util.Random;

/**
 * Text Renderer
 *
 * @author Roj234
 * @since 2021/2/3 19:40
 */
public class TextRenderer {
	public static final int[] COLOR_CODE;
	public static final Random FONT_RND;
	public static final MyBitSet COLOR_CODE_TEXT = MyBitSet.from('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'k', 'l', 'm', 'n', 'o', 'r');

	static {
		COLOR_CODE = new int[32];
		for (int i = 0; i < 32; ++i) {
			int j = (i >> 3 & 1) * 85;
			int k = (i >> 2 & 1) * 170 + j;
			int l = (i >> 1 & 1) * 170 + j;
			int i1 = (i >> 0 & 1) * 170 + j;
			if (i == 6) {
				k += 85;
			}

			if (i >= 16) {
				k /= 4;
				l /= 4;
				i1 /= 4;
			}

			COLOR_CODE[i] = (k & 255) << 16 | (l & 255) << 8 | i1 & 255;
		}
		FONT_RND = new Random();
	}

	public final FontTex font;
	private final VertexBuilder vb;
	private final int[] colorCode;

	public float scale = 1.0f;
	public int charSpace = 1;

	public int lastTexture, lineHeight;

	public TextRenderer(FontTex font, VertexBuilder vb) {
		this.font = font;
		this.vb = vb;
		this.colorCode = COLOR_CODE;
	}

	public TextRenderer(FontTex font, int[] colorCode, VertexBuilder vb) {
		this.font = font;
		this.vb = vb;
		this.colorCode = colorCode;
	}

	public VertexBuilder getVertexBuilder() {
		return vb;
	}

	public void renderString(CharSequence text, float posX, float posY) {
		renderString(text, posX, posY, 0xFF000000);
	}
	public void renderString(CharSequence text, float posX, float posY, int color) {
		if (scale != 1) {
			GL11.glPushMatrix();
			GL11.glScaled(scale, scale, scale);
			posX *= 1f / scale;
			posY *= 1f / scale;
			doRender(text, false, posX, posY, color);
			GL11.glPopMatrix();
		} else {
			doRender(text, false, posX, posY, color);
		}
	}
	public void renderStringWithShadow(CharSequence text, float posX, float posY) {
		renderStringWithShadow(text, posX, posY, -1);
	}
	public void renderStringWithShadow(CharSequence text, float posX, float posY, int color) {
		if (scale != 1) {
			GL11.glPushMatrix();
			GL11.glScaled(scale, scale, scale);
			posX *= 1f / scale;
			posY *= 1f / scale;
			doRender(text, true, posX+1, posY-1, color);
			doRender(text, false, posX, posY, color);
			GL11.glPopMatrix();
		} else {
			doRender(text, true, posX+1, posY-1, color);
			doRender(text, false, posX, posY, color);
		}
	}

	public void renderStringCenterX(CharSequence text, float posY, float width, int color) {
		renderString(text, (width - getStringWidth(text)) / 2, posY, color);
	}

	public final float doRender(CharSequence text, boolean shadow, float posX, float posY, int color) {
		lastTexture = -1;
		lineHeight = font.preRender(text, 0, text.length());

		VertexBuilder vb = this.vb;
		vb.begin(VertexFormat.POSITION_TEX);

		if ((color & 0xFF000000) == 0) {
			color = GL11.glGetInteger(GL11.GL_COLOR);
		} else {
			if (shadow) color = (color & 0xFCFCFC) >>> 2 | (color & 0xFF000000);
			colorHook(color);
		}
		final int color0 = color;

		int flag = 0, lastFlag = 0;
		float lastX = 0;

		for (int i = 0; i < text.length(); ++i) {
			if (lastFlag != (flag & 6)) { // flag changed
				if (lastFlag != 0) {
					vb.end();
					VboUtil.drawCPUVertexes(GL11.GL_QUADS, vb);

					// flush and prepare
					this.i_drawLine(lastFlag, lastX, posY, posX - lastX);

					vb.begin(VertexFormat.POSITION_TEX);
				}
				lastX = posX;
				lastFlag = flag & 6;
			}

			char c = text.charAt(i);
			if (c == '\u00a7' && i + 1 < text.length()) {
				if (COLOR_CODE_TEXT.contains(c = Character.toLowerCase(text.charAt(i + 1)))) {
					int tColor = color;
					if (c <= 'f') {
						flag = 0;
						int index = TextUtil.c2i(c);
						if (index == -1) index = c - 'a' + 10;

						if (shadow) {
							index += 16;
						}

						tColor = colorCode[index];
					} else {
						switch (c) {
							case 'r':
								flag = 0;
								tColor = color0;
								break;
							case 'l':
								flag |= 1;
								break;
							case 'm':
								flag |= 2;
								break;
							case 'n':
								flag |= 4;
								break;
							case 'o':
								flag |= 8;
								break;
							case 'k':
								flag |= 16;
								break;
						}
					}
					if (tColor != color) {
						vb.end();
						VboUtil.drawCPUVertexes(GL11.GL_QUADS, vb);

						if (lastFlag != 0) {
							this.i_drawLine(lastFlag, lastX, posY, posX - lastX);
							lastX = posX;
						}

						vb.begin(VertexFormat.POSITION_TEX);
						colorHook(color = 0xFF000000 | tColor);
					}
					++i;
					continue;
				}
			}

			if ((flag & 16) != 0) {
				int w = getCharWidth(c);

				IntList list = font.getEntriesByWidth(w);
				if (list.size() > 0) c = (char) list.get(FONT_RND.nextInt(list.size()));
			}

			vb.translate(posX, posY, 0);
			float xLen = this.i_renderChar(c, (flag & 8) != 0 ? lineHeight / 8 : 0);

			if ((flag & 1) != 0) { // bold
				vb.translate(posX + (shadow ? 0.5f : 1f), posY, 0);

				this.i_renderChar(c, (flag & 8) != 0 ? lineHeight / 8 : 0);

				xLen++;
			}

			posX += xLen + charSpace;
		}

		vb.end();
		VboUtil.drawCPUVertexes(GL11.GL_QUADS, vb);
		if (lastFlag != 0) this.i_drawLine(lastFlag, lastX, posY, posX - lastX);

		return posX;
	}

	public int getCharWidth(char c) {
		return (int) Math.floor(getRawWidth(c) * scale);
	}
	public float getCharWidthFloat(char c) {
		return getRawWidth(c) * scale;
	}

	private int getRawWidth(char c) {
		switch (c) {
			case ' ': case ' ': return font.getCharWidth('1');
			case 167: return -1;
		}
		return font.getCharWidth(c);
	}

	public float getStringWidth(CharSequence s) {
		int w = 0;
		for (int i = 0; i < s.length(); i++) {
			int w1 = getRawWidth(s.charAt(i));
			if (w1 < 0) i++;
			else w += w1 + charSpace;
		}
		return w * scale;
	}

	public final float i_renderChar(char ch, float italic) {
		switch (ch) {
			case ' ': case ' ': return font.getCharWidth('1');
		}

		Glyph tex = font.getOrCreateEntry(ch);
		if (tex == null) return 0;

		VertexBuilder vb = this.vb;
		if (lastTexture != tex.textureId) {
			VboUtil.drawCPUVertexes(GL11.GL_QUADS, vb);
			bindTextureHook(tex.textureId);
			vb.begin(VertexFormat.POSITION_TEX);
			lastTexture = tex.textureId;
		}

		float x = tex.xOff;
		float y = tex.baseline;
		float w = tex.width;
		float h = tex.height;
		vb.pos(x+italic, y, 0).tex(tex.u1, tex.v1).endVertex();
		vb.pos(x-italic, y+h, 0).tex(tex.u1, tex.v2).endVertex();
		vb.pos(x+w-italic, y+h, 0).tex(tex.u2, tex.v2).endVertex();
		vb.pos(x+w+italic, y, 0).tex(tex.u2, tex.v1).endVertex();
		return x*2+w;
	}

	protected void bindTextureHook(int id) {
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
	}
	protected void disableTex2dHook() {
		GL11.glDisable(GL11.GL_TEXTURE_2D);
	}
	protected void enableTex2dHook() {
		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}
	protected void colorHook(int color) {
		Util.color(color);
	}

	public void i_drawLine(int flag, float posX, float posY, float len) {
		VertexBuilder vb = this.vb;

		disableTex2dHook();

		vb.end();
		vb.begin(VertexFormat.POSITION);

		// 删除线
		if ((flag & 2) != 0) {
			float fhDiv2 = lineHeight / 2f;
			vb.pos(posX, posY + fhDiv2, 0).endVertex();
			vb.pos(posX + len, posY + fhDiv2, 0).endVertex();
			vb.pos(posX + len, posY + fhDiv2 - 1, 0).endVertex();
			vb.pos(posX, posY + fhDiv2 - 1, 0).endVertex();
		}

		// 下划线
		if ((flag & 4) != 0) {
			float fhDiv2 = lineHeight + 1;
			vb.pos(posX - 1, posY + fhDiv2, 0).endVertex();
			vb.pos(posX + len, posY + fhDiv2, 0).endVertex();
			vb.pos(posX + len, posY + fhDiv2 - 1, 0).endVertex();
			vb.pos(posX - 1, posY + fhDiv2 - 1, 0).endVertex();
		}
		VboUtil.drawCPUVertexes(GL11.GL_QUADS, vb);

		enableTex2dHook();
	}

	public void setFontSize(float lineHeight) {
		int maxHeight = font.maxLineHeight;
		scale = lineHeight / maxHeight;
	}
}
