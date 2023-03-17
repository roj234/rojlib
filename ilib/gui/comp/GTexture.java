package ilib.gui.comp;

import ilib.client.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.util.Sprite;

import java.awt.*;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GTexture extends SimpleComponent {
	protected int u, v;
	protected Color overlay;

	public GTexture(IGui parent, int x, int y, int u, int v, int w, int h) {
		super(parent, x, y, w, h);
		this.u = u;
		this.v = v;
	}

	public GTexture(Component relativeTo, Sprite sprite) {
		super(relativeTo.owner, relativeTo.xPos + sprite.offsetX(), relativeTo.yPos + sprite.offsetY(), sprite.w(), sprite.h());
		u = sprite.u();
		v = sprite.v();
		setTexture(sprite.texture());
	}

	public GTexture(IGui parent, int x, int y, Sprite sprite) {
		super(parent, x, y, sprite.w(), sprite.h());
		u = sprite.u();
		v = sprite.v();
		setTexture(sprite.texture());
	}

	/*******************************************************************************************************************
	 * Overrides                                                                                                       *
	 *******************************************************************************************************************/

	@Override
	public void render(int mouseX, int mouseY) {
		if (isVisible()) {
			RenderUtils.bindTexture(getTexture());
			if (overlay != null) RenderUtils.setColor(overlay);
			drawTexturedModalRect(xPos, yPos, u, v, width, height);
		}
	}

	protected boolean isVisible() {
		return true;
	}

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	public int getU() {
		return u;
	}

	public void setU(int u) {
		this.u = u;
	}

	public int getV() {
		return v;
	}

	public void setV(int v) {
		this.v = v;
	}

	public Color getOverlay() {
		return overlay;
	}

	public GTexture setOverlay(Color overlay) {
		this.overlay = overlay;
		return this;
	}
}
