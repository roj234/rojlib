package ilib.gui.comp;

import ilib.client.RenderUtils;
import ilib.gui.IGui;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.GlStateManager;

import javax.annotation.Nullable;
import java.awt.*;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GColoredQuad extends SimpleComponent {
	protected Color color;
	protected int zIndex;

	public GColoredQuad(IGui parent, int x, int y, int w, int h, Color color) {
		super(parent, x, y, w, h);
		this.color = color;
		this.zIndex = 10;
	}

	/*******************************************************************************************************************
	 * Component                                                                                                       *
	 *******************************************************************************************************************/

	@Nullable
	protected Color getDynamicColor() {
		return color;
	}

	@Override
	public void render(int mouseX, int mouseY) {
		color = getDynamicColor();
		if (color == null || color.getAlpha() == 0) return;

		if (color.getAlpha() != 255) {
			GlStateManager.enableBlend();
			GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		}

		GlStateManager.disableTexture2D();

		RenderUtils.setColor(color);

		GL11.glBegin(GL11.GL_QUADS);
		GL11.glVertex3d(xPos, yPos, zIndex);
		GL11.glVertex3d(xPos, yPos + height, zIndex);
		GL11.glVertex3d(xPos + width, yPos + height, zIndex);
		GL11.glVertex3d(xPos + width, yPos, zIndex);
		GL11.glEnd();

		GlStateManager.enableTexture2D();
	}

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public int getZIndex() {
		return zIndex;
	}

	public GColoredQuad setZIndex(int zIndex) {
		this.zIndex = zIndex;
		return this;
	}
}
