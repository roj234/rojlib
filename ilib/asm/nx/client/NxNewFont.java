package ilib.asm.nx.client;

import ilib.Config;
import ilib.asm.util.DelegatingTextRender;
import ilib.asm.util.IFontRenderer;
import org.lwjgl.opengl.GL11;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.io.IOUtil;
import roj.opengl.text.FontTex;
import roj.opengl.text.TextRenderer;
import roj.text.CharList;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

/**
 * Roj234 Font Renderer
 *
 * @author Roj234
 * @since 2021/2/3 19:40
 */
@Nixim(value = "/", copyItf = true)
public class NxNewFont extends FontRenderer implements IFontRenderer {
	@Copy
	TextRenderer rojTextRender;

	@Shadow
	int[] colorCode;

	@Shadow
	boolean unicodeFlag;

	@Inject(value = "<init>", at = Inject.At.TAIL)
	public NxNewFont(GameSettings g, ResourceLocation l, TextureManager tx, boolean u) {
		super(g, l, tx, u);
		setFont(new FontTex(Config.customFont + "-16"));
	}

	@Inject(flags = Inject.FLAG_OPTIONAL)
	private void renderStringAtPos(String text, boolean shadow) {
		posX = rojTextRender.doRender(text, shadow, posX, posY, 0);
	}

	@Inject("/")
	public int drawString(String text, float x, float y, int color, boolean dropShadow) {
		GlStateManager.pushMatrix();

		boolean isBlendEnabled = GL11.glGetBoolean(GL11.GL_BLEND);
		if (!isBlendEnabled) {
			GL11.glEnable(GL11.GL_BLEND);
		}
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

		float scale = rojTextRender.scale;
		GlStateManager.scale(scale, scale, scale);
		x *= 1f / scale;
		y *= 1f / scale;

		if ((color & -67108864) == 0) {
			color |= -16777216;
		}

		int i;
		if (dropShadow) {
			i = (int) rojTextRender.doRender(text, true, posX = x + scale, posY = y + scale, color);
			i = Math.max(i, (int) rojTextRender.doRender(text, false, posX = x, posY = y, color));
		} else {
			i = (int) rojTextRender.doRender(text, false, posX = x, posY = y, color);
		}
		posX = i;

		if (!isBlendEnabled) {
			GL11.glDisable(GL11.GL_BLEND);
		}

		GlStateManager.popMatrix();

		return i;
	}

	@Inject
	private int renderString(String text, float x, float y, int color, boolean dropShadow) {
		if (text == null) {
			return 0;
		} else {
			if ((color & -67108864) == 0) {
				color |= -16777216;
			}

			if (dropShadow) {
				color = (color & 16579836) >> 2 | color & -16777216;
			}

			posX = x;
			posY = y;
			posX = rojTextRender.doRender(text, dropShadow, posX, posY, color);
			return (int) posX;
		}
	}

	@Inject("/")
	public int getStringWidth(String text) {
		if (text == null || text.length() == 0) {
			return 0;
		} else {
			int i = 0, l = text.length();
			boolean Lflag = false;

			for (int j = 0; j < l; ++j) {
				char c = text.charAt(j);
				int w = this.getCharWidth(c);
				if (w < 0 && j < l - 1) {
					c = text.charAt(++j);
					if (c != 'l' && c != 'L') {
						if (c == 'r' || c == 'R') {
							Lflag = false;
						}
					} else {
						Lflag = true;
					}
				} else {
					i += w;
					if (Lflag && w > 0) {
						++i;
					}
				}
			}

			return i;
		}
	}

	@Inject("/")
	public int getCharWidth(char c) {
		return rojTextRender.getCharWidth(c);
	}

	@Inject
	private float renderChar(char ch, boolean italic) {
		return rojTextRender.i_renderChar(ch, italic ? 1 : 0);
	}

	@Inject("/")
	protected float renderDefaultChar(int ch, boolean italic) {
		throw new NoSuchMethodError();
	}

	@Inject("/")
	protected float renderUnicodeChar(char ch, boolean italic) {
		return rojTextRender.i_renderChar(ch, italic ? 1 : 0);
	}

	@Inject("/")
	protected void doDraw(float f) {
		throw new NoSuchMethodError();
	}

	// moved to NxFastFont

	@Override
	@Copy
	public void setFont(FontTex unicode) {
		if (rojTextRender != null) {
			rojTextRender.getVertexBuilder().free();
			rojTextRender.font.deleteGlResource();
		}
		unicode.fallback = FontTex.DEFAULT_FONT;
		rojTextRender = new DelegatingTextRender(unicode, colorCode);

		// pre-render ASCII-SGA
		CharList tmp = IOUtil.getSharedCharBuf();
		for (int i = 0; i < 256; i++) {
			tmp.append((char) i);
		}
		rojTextRender.font.preRender(tmp.toString(), 0, 256);
	}
}
