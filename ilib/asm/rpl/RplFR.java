package ilib.asm.rpl;

import ilib.asm.Loader;
import roj.asm.nixim.Inject;
import roj.io.IOUtil;
import roj.opengl.text.FontTex;
import roj.opengl.text.TextRenderer;
import roj.opengl.vertex.VertexBuilder;
import roj.text.CharList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

/**
 * Roj234 Font Renderer
 *
 * @author Roj234
 * @since 2021/2/3 19:40
 */
public class RplFR extends FontRenderer implements Runnable {
	public static final class ZeroInputStream extends InputStream {
		private int i;

		public ZeroInputStream(int i) { this.i = i; }

		@Override
		public int read() throws IOException {
			if (i == 0) return -1;
			i--;
			return 0;
		}

		@Override
		public int read(@Nonnull byte[] b, int off, int len) {
			if (i == 0) return -1;
			int toRead = Math.min(i, len);
			i -= toRead;
			return toRead;
		}
	}

	private final TextRenderer tr;

	public RplFR() {
		super(Minecraft.getMinecraft().gameSettings, null, null, true);

		FontTex fnt = new FontTex("宋体-16");
		fnt.fallback = FontTex.DEFAULT_FONT;
		tr = new TextRenderer(fnt, new VertexBuilder(2048));

		CharList tmp = IOUtil.getSharedCharBuf();
		for (int i = 32; i < 128; i++) tmp.append((char) i);
		tr.font.preRender(tmp, 0, tmp.length());
		tr.setFontSize(9);

		Loader.EVENT_BUS.add("LoadCompleteEx", this);
	}

	protected void bindTexture(ResourceLocation r) {}
	public void onResourceManagerReload(IResourceManager resourceManager) {}

	@Override
	protected IResource getResource(ResourceLocation location) {
		return new SimpleResource(null, location, new ZeroInputStream(0xFFFF), null, null);
	}

	@Inject("/")
	public int drawString(String text, float x, float y, int color, boolean dropShadow) {
		GlStateManager.pushMatrix();

		GlStateManager.enableAlpha();

		float scale = tr.scale;
		GlStateManager.scale(scale, scale, scale);
		x *= 1f / scale;
		y *= 1f / scale;

		if ((color & -67108864) == 0) {
			color |= -16777216;
		}

		int i;
		if (dropShadow) {
			i = (int) tr.doRender(text, true, posX = x + scale, posY = y + scale, color);
			i = Math.max(i, (int) tr.doRender(text, false, posX = x, posY = y, color));
		} else {
			i = (int) tr.doRender(text, false, posX = x, posY = y, color);
		}
		posX = i;

		GlStateManager.popMatrix();

		return i;
	}

	public int getStringWidth(String text) { return (int) tr.getStringWidth(text); }
	public int getCharWidth(char c) { return tr.getCharWidth(c); }

	protected float renderDefaultChar(int ch, boolean italic) { throw new NoSuchMethodError(); }
	protected float renderUnicodeChar(char ch, boolean italic) { throw new NoSuchMethodError(); }
	protected void doDraw(float f) { throw new NoSuchMethodError(); }

	public void run() {
		tr.getVertexBuilder().free();
		tr.font.deleteGlResource();
	}
}
