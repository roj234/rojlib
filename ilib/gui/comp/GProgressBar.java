package ilib.gui.comp;

import ilib.client.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.util.Direction;
import ilib.gui.util.Sprite;

/**
 * @author Roj234
 * @since 2021/1/13 12:37
 */
public class GProgressBar extends GTexture {
	protected Direction direction;
	protected Provider provider;

	public GProgressBar(IGui parent, int x, int y, int u, int v, int w, int h, Direction dir) {
		super(parent, x, y, u, v, w, h);
		this.direction = dir;
	}

	public GProgressBar(Component relativeTo, Sprite sprite, Direction dir) {
		super(relativeTo, sprite);
		this.direction = dir;
	}

	public GProgressBar(IGui parent, int x, int y, Sprite sprite, Direction dir) {
		super(parent, x, y, sprite);
		this.direction = dir;
	}

	/**
	 * @param length What to scale to
	 */
	protected int getProgress(int length) {
		return provider.getProgress(this, length);
	}

	/*******************************************************************************************************************
	 * Overrides                                                                                                       *
	 *******************************************************************************************************************/

	@Override
	public void render(int mouseX, int mouseY) {
		RenderUtils.bindTexture(getTexture());

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

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	public Direction getDirection() {
		return direction;
	}

	public void setDirection(Direction direction) {
		this.direction = direction;
	}

	public Provider getProvider() {
		return provider;
	}

	public GProgressBar setProvider(Provider provider) {
		this.provider = provider;
		return this;
	}

	public interface Provider {
		int getProgress(GProgressBar sel, int length);
	}
}
