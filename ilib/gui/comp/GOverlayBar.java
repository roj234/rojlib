package ilib.gui.comp;

import ilib.client.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.util.Direction;
import ilib.gui.util.Sprite;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GOverlayBar extends GProgressBar {
	protected Sprite bg, fg;

	public GOverlayBar(IGui parent, int x, int y, Sprite fg, Sprite bg, Direction dir) {
		super(parent, x, y, bg, dir);
		this.bg = bg;
		this.fg = fg;
	}

	public GOverlayBar(Component relativeTo, Sprite fg, Sprite bg, Direction dir) {
		super(relativeTo, bg, dir);
		this.bg = bg;
		this.fg = fg;
	}

	@Override
	public void render(int mouseX, int mouseY) {
		RenderUtils.bindTexture(getTexture());

		drawTexturedModalRect(xPos, yPos, bg.u(), bg.v(), width, height);

		int xPos = this.xPos + fg.offsetX();
		int yPos = this.yPos + fg.offsetY();
		int u = fg.u();
		int v = fg.v();
		int width = fg.w();
		int height = fg.h();

		RenderUtils.bindTexture(fg.texture());

		int p;
		switch (direction) {
			case UP:
				p = Math.min(height, getProgress(height));
				drawTexturedModalRect(xPos, height - p + yPos, u, v + height - p, width, p);
				break;
			case DOWN:
				p = Math.min(height, getProgress(height));
				drawTexturedModalRect(xPos, yPos, u, v, width, p);
				break;
			case LEFT:
				p = Math.min(width, getProgress(width));
				drawTexturedModalRect(xPos + -width + p, yPos, u, v, p, height);
				break;
			case RIGHT:
				p = Math.min(width, getProgress(width));
				drawTexturedModalRect(xPos, yPos, u, v, p, height);
				break;
		}
	}
}
