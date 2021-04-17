package ilib.gui.comp;

import ilib.gui.IGui;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GTextureShiny extends GTexture {
	private boolean visible = true;
	private int tick;

	public GTextureShiny(IGui parent, int x, int y, int u, int v, int w, int h) {
		super(parent, x, y, u, v, w, h);
	}

	@Override
	protected boolean isVisible() {
		if (!isActive()) return false;
		if (++tick > maxTick()) {
			visible = !visible;
			tick = 0;
		}
		return visible;
	}

	protected int maxTick() {
		return 0;
	}

	protected boolean isActive() {
		return true;
	}
}
