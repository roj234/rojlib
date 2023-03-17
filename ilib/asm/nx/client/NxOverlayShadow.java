package ilib.asm.nx.client;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

import net.minecraftforge.client.GuiIngameForge;

import java.awt.*;

/**
 * @author solo6975
 * @since 2022/5/2 16:05
 */
@Nixim("net.minecraftforge.client.GuiIngameForge")
abstract class NxOverlayShadow extends GuiIngameForge {
	@Shadow("/")
	private FontRenderer fontrenderer;

	NxOverlayShadow(Minecraft mc) {
		super(mc);
	}

	@Inject("/")
	protected void renderRecordOverlay(int width, int height, float partialTicks) {
		if (this.overlayMessageTime > 0) {
			this.mc.profiler.startSection("overlayMessage");
			float hue = (float) overlayMessageTime - partialTicks;
			int opacity = (int) (hue * 256.0F / 20.0F);
			if (opacity > 255) {
				opacity = 255;
			}

			if (opacity > 8) {
				GlStateManager.pushMatrix();
				GlStateManager.translate((float) (width / 2), (float) (height - 68), 0.0F);
				GlStateManager.enableBlend();
				GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
													GlStateManager.DestFactor.ZERO);

				int color = animateOverlayMessageColor ? Color.HSBtoRGB(hue / 50.0F, 0.7F, 0.6F) & 16777215 : 16777215;
				fontrenderer.drawStringWithShadow(overlayMessage, -fontrenderer.getStringWidth(overlayMessage) / 2, -4, color | opacity << 24);
				GlStateManager.disableBlend();
				GlStateManager.popMatrix();
			}

			mc.profiler.endSection();
		}
	}
}
