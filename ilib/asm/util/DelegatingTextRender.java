package ilib.asm.util;

import roj.opengl.text.FontTex;
import roj.opengl.text.TextRenderer;
import roj.opengl.vertex.VertexBuilder;

import net.minecraft.client.renderer.GlStateManager;

/**
 * @author Roj233
 * @since 2022/5/24 0:18
 */
public class DelegatingTextRender extends TextRenderer {
	public DelegatingTextRender(FontTex unicode, int[] colorCode) {
		super(unicode, colorCode, new VertexBuilder(2048));
		scale = 16f / unicode.getFont().getSize();
	}

	@Override
	protected void bindTextureHook(int id) {
		GlStateManager.bindTexture(id);
	}

	@Override
	protected void disableTex2dHook() {
		GlStateManager.disableTexture2D();
	}

	@Override
	protected void enableTex2dHook() {
		GlStateManager.enableTexture2D();
	}

	@Override
	protected void colorHook(int color) {
		staticColorhook(color);
	}

	public static void staticColorhook(int color) {
		float alpha = (color >> 24 & 0xFF) / 255F;
		float red = (color >> 16 & 0xFF) / 255F;
		float green = (color >> 8 & 0xFF) / 255F;
		float blue = (color & 0xFF) / 255F;
		GlStateManager.color(red, green, blue, alpha);
	}
}
