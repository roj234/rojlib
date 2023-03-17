package ilib.gui.comp;

import ilib.ClientProxy;
import ilib.client.RenderUtils;
import ilib.gui.DefaultSprites;
import ilib.gui.GuiHelper;
import ilib.gui.IGui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class GReverseTab extends GTab {
	private int prevX;

	public GReverseTab(IGui parent, int x, int y, int exWidth, int exHeight, @Nullable ItemStack stack) {
		super(parent, x, y, exWidth, exHeight, stack);
		prevX = x;
	}

	GReverseTab(IGui parent, int x, int y) {
		super(parent, x, y);
		prevX = x;
	}

	@Override
	public void render(int mouseX, int mouseY) {
		RenderUtils.setColor(color);
		DefaultSprites.TAB.render(xPos, yPos, 0, 9, width, height);

		if (stack != null) {
			RenderHelper.enableGUIStandardItemLighting();

			ClientProxy.mc.getRenderItem().renderItemAndEffectIntoGUI(stack, xPos + width - 20, yPos + 4);

			RenderHelper.disableStandardItemLighting();
		}

		if (isActive()) {
			GlStateManager.pushMatrix();
			GlStateManager.translate(xPos, yPos, 0);
			GuiHelper.renderBackground(mouseX - xPos, mouseY - yPos, components);
			GlStateManager.popMatrix();
		}

		animateFold();
		xPos = prevX - width + FOLDED_SIZE;
	}

	@Override
	public void setXPos(int xPos) {
		prevX = xPos;
	}
}
