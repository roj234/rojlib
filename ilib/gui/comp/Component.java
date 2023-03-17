package ilib.gui.comp;

import ilib.ClientProxy;
import ilib.anim.Animation;
import ilib.client.RenderUtils;
import ilib.gui.DefaultSprites;
import ilib.gui.IGui;
import ilib.gui.util.ComponentListener;
import org.lwjgl.opengl.GL11;
import roj.collect.SimpleList;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

import java.awt.*;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public abstract class Component {
	public static final ResourceLocation TEXTURE = DefaultSprites.TEXTURE;

	static final List<String> tooltip = new SimpleList<>();

	protected IGui owner;

	protected int mark;
	protected int xPos, yPos;

	protected ResourceLocation texture;

	protected ComponentListener listener;

	protected Animation animation;

	public Component(IGui parent) {
		owner = parent;

		if (parent instanceof ComponentListener) setListener((ComponentListener) parent);

		texture = TEXTURE;
	}

	public Component(IGui parent, int x, int y) {
		owner = parent;

		if (parent instanceof ComponentListener) setListener((ComponentListener) parent);

		texture = TEXTURE;
		xPos = x;
		yPos = y;
	}

	public void onInit() {
		if (listener != null) listener.componentInit(this);
	}

	/**
	 * Called to render the component
	 */
	public abstract void render(int mouseX, int mouseY);

	/**
	 * Called after base render, is already translated to guiLeft and guiTop, just move offset
	 */
	public abstract void render2(int mouseX, int mouseY);

	public void getDynamicTooltip(List<String> tooltip, int mouseX, int mouseY) {
		if (listener != null) listener.getDynamicTooltip(this, tooltip, mouseX, mouseY);
	}

	public void renderTooltip(int relX, int relY, int absX, int absY) {
		tooltip.clear();
		getDynamicTooltip(tooltip, relX, relY);
		if (!tooltip.isEmpty()) drawHoveringText(tooltip, absX, absY, ClientProxy.mc.fontRenderer);
	}

	/**
	 * Used to get what area is being displayed, mainly used for JEI
	 */
	public Rectangle getArea(int guiLeft, int guiTop) {
		return new Rectangle(xPos + guiLeft, yPos + guiTop, getWidth(), getHeight());
	}

	public void mouseDown(int x, int y, int button) {
		if (listener != null) listener.mouseDown(this, x, y, button);
	}

	public static void drawTexturedModalRect(int x, int y, int u, int v, int w, int h) {
		RenderUtils.fastRect(x, y, u, v, w, h);
	}

	public void mouseUp(int x, int y, int button) {
		if (listener != null) listener.mouseUp(this, x, y, button);
	}

	/**
	 * @param time How long
	 */
	public void mouseDrag(int x, int y, int button, long time) {
		if (listener != null) listener.mouseDrag(this, x, y, button, time);
	}

	/**
	 * Called when the mouse is scrolled
	 *
	 * @param dir 1 for positive, -1 for negative
	 */
	public void mouseScrolled(int x, int y, int dir) {
		if (listener != null) listener.mouseScrolled(this, x, y, dir);
	}

	public boolean isMouseOver(int mouseX, int mouseY) {
		return checkMouseOver(mouseX, mouseY);
	}

	protected final boolean checkMouseOver(int mouseX, int mouseY) {
		return mouseX >= xPos && mouseX < xPos + getWidth() && mouseY >= yPos && mouseY < yPos + getHeight();
	}

	public void keyTyped(char letter, int keyCode) {}

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	public abstract int getWidth();

	public abstract int getHeight();

	public final int getXPos() {
		return xPos;
	}

	public void setXPos(int xPos) {
		this.xPos = xPos;
	}

	public final int getYPos() {
		return yPos;
	}

	public void setYPos(int yPos) {
		this.yPos = yPos;
	}

	public final Component alignCenterY() {
		yPos = (owner.getHeight() - getHeight()) / 2;
		return this;
	}

	public final Component alignCenterX() {
		xPos = (owner.getWidth() - getWidth()) / 2;
		return this;
	}

	public ResourceLocation getTexture() {
		return this.texture;
	}

	@SuppressWarnings("unchecked")
	public <T extends Component> T setTexture(ResourceLocation loc) {
		this.texture = loc;
		return (T) this;
	}

	public final IGui getOwner() {
		return owner;
	}

	public final void setOwner(IGui owner) {
		this.owner = owner;
	}

	public final ComponentListener getListener() {
		return listener;
	}

	public Component setListener(ComponentListener l) {
		this.listener = l;
		return this;
	}

	// 方便用switch
	public int getMark() {
		return mark;
	}

	public Component setMark(int mark) {
		this.mark = mark;
		return this;
	}

	public Animation getAnimation() {
		return animation;
	}

	public void setAnimation(Animation a) {
		this.animation = a;
	}

	/*******************************************************************************************************************
	 * Helper Methods                                                                                                  *
	 *******************************************************************************************************************/

	protected final void drawHoveringText(List<String> tip, int mouseX, int mouseY, FontRenderer fr) {
		if (!tip.isEmpty()) {
			int maxWidth = 0;
			for (int i = 0; i < tip.size(); i++) {
				int width = fr.getStringWidth(tip.get(i));
				if (width > maxWidth) maxWidth = width;
			}

			int x = mouseX + 12;
			int y = mouseY - 12;

			int dY = 8;
			if (tip.size() > 1) {
				dY += 2 + (tip.size() - 1) * 10;
			}
			if (x + maxWidth > owner.getWidth()) {
				x -= 28 + maxWidth;
			}
			//            if (y + dY + 6 > owner.getHeight()) {
			//                y = owner.getHeight() - dY - 6;
			//            }

			GlStateManager.disableTexture2D();
			GlStateManager.enableBlend();
			GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
			GlStateManager.shadeModel(GL11.GL_SMOOTH);

			drawTipBG(x - 3, y - 4, x + maxWidth + 3, y - 3);
			drawTipBG(x - 3, y + dY + 3, x + maxWidth + 3, y + dY + 4);
			drawTipBG(x - 3, y - 3, x + maxWidth + 3, y + dY + 3);
			drawTipBG(x - 4, y - 3, x - 3, y + dY + 3);
			drawTipBG(x + maxWidth + 3, y - 3, x + maxWidth + 4, y + dY + 3);

			drawTipFG(x - 3, y - 3 + 1, x - 3 + 1, y + dY + 3 - 1);
			drawTipFG(x + maxWidth + 2, y - 3 + 1, x + maxWidth + 3, y + dY + 3 - 1);
			drawTipFG(x - 3, y - 3, x + maxWidth + 3, y - 3 + 1);
			drawTipFG(x - 3, y + dY + 2, x + maxWidth + 3, y + dY + 3);

			GlStateManager.shadeModel(GL11.GL_FLAT);
			GlStateManager.disableBlend();
			GlStateManager.enableTexture2D();

			GlStateManager.pushMatrix();
			GlStateManager.disableLighting();
			GlStateManager.translate(0, 0, 301);

			for (int i = 0; i < tip.size(); i++) {
				String s = tip.get(i);
				fr.drawStringWithShadow(s, x, y, -1);
				if (i == 0) y += 2;
				y += 10;
			}

			GlStateManager.popMatrix();
		}
	}

	protected static void drawTipBG(int a, int b, int c, int d) {
		final int z = 300;

		BufferBuilder buf = RenderUtils.BUILDER;
		buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		buf.pos(c, b, z).color(0, 16, 16, 240).endVertex();
		buf.pos(a, b, z).color(0, 16, 16, 240).endVertex();
		buf.pos(a, d, z).color(0, 16, 16, 240).endVertex();
		buf.pos(c, d, z).color(0, 16, 16, 240).endVertex();
		RenderUtils.TESSELLATOR.draw();
	}

	protected static void drawTipFG(int a, int b, int c, int d) {
		final int z = 300;

		BufferBuilder buf = RenderUtils.BUILDER;
		buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		buf.pos(c, b, z).color(0, 80, 80, 255).endVertex();
		buf.pos(a, b, z).color(0, 80, 80, 255).endVertex();
		buf.pos(a, d, z).color(0, 40, 80, 127).endVertex();
		buf.pos(c, d, z).color(0, 40, 80, 127).endVertex();
		RenderUtils.TESSELLATOR.draw();
	}
}
