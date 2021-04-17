package ilib.asm.nx.client;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import net.minecraftforge.client.GuiIngameForge;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/5/2 16:05
 */
@Nixim("net.minecraftforge.client.GuiIngameForge")
abstract class NxTooltip extends GuiIngameForge {
	@Shadow("/")
	private FontRenderer fontrenderer;

	NxTooltip(Minecraft mc) {
		super(mc);
	}

	@Inject("/")
	protected void renderToolHighlight(ScaledResolution res) {
		if (!this.mc.playerController.isSpectator()) {
			if (this.mc.gameSettings.heldItemTooltips) {
				this.mc.profiler.startSection("toolHighlight");
				ItemStack stack = highlightingItemStack;
				if (this.remainingHighlightTicks > 0 && !stack.isEmpty()) {
					String name = stack.getDisplayName();
					if (stack.hasDisplayName()) {
						name = TextFormatting.ITALIC + name;
					}

					name = stack.getItem().getHighlightTip(stack, name);
					int opacity = (int) ((float) this.remainingHighlightTicks * 256.0F / 10.0F);
					if (opacity > 255) {
						opacity = 255;
					}

					if (opacity > 0) {
						int y = res.getScaledHeight() - 59;
						if (!mc.playerController.shouldDrawHUD()) {
							y += 14;
						}

						GlStateManager.pushMatrix();
						GlStateManager.enableBlend();
						GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
															GlStateManager.DestFactor.ZERO);

						FontRenderer fr = stack.getItem().getFontRenderer(stack);
						if (fr == null) fr = fontrenderer;

						List<String> tmp = stack.getTooltip(mc.player, ITooltipFlag.TooltipFlags.NORMAL);
						tmp.set(0, name);
						for (int i = 1; i < tmp.size(); i++) {
							String s = tmp.get(i);
							if (!s.isEmpty() && s.charAt(0) != '\u00a7') {
								tmp.set(i, TextFormatting.GRAY + s);
							}
						}
						y -= 10 * (tmp.size() - 1);
						for (int i = 0; i < tmp.size(); i++) {
							name = tmp.get(i);
							int x = (res.getScaledWidth() - fr.getStringWidth(name)) / 2;
							fr.drawStringWithShadow(name, x, y, 16777215 | opacity << 24);
							y += 10;
						}

						GlStateManager.disableBlend();
						GlStateManager.popMatrix();
					}
				}

				this.mc.profiler.endSection();
			}
		} else if (this.mc.player.isSpectator()) {
			this.spectatorGui.renderSelectedItem(res);
		}
	}
}
