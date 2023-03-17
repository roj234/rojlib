package ilib.gui.comp;

import ilib.client.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.util.ComponentListener;
import roj.math.MathUtils;

/**
 * @author Roj234
 * @since 2022/10/29 14:21
 */
public class GTextureAnimated extends GTexture {
	private boolean active;
	private int tick, ticks;
	private int frame, frames;
	private boolean byYpos;

	public GTextureAnimated(IGui parent, int x, int y, int u, int v, int w, int h, int ticks, int frames) {
		super(parent, x, y, u, v, w, h);
		this.ticks = ticks;
		this.frames = frames;
	}

	@Override
	public void render(int mouseX, int mouseY) {
		if (active) {
			if (--tick == 0) {
				tick = ticks;

				if (frame++ == frames) {
					frame = 0;

					if (listener != null) listener.actionPerformed(this, ComponentListener.ANIMATE_TEXTURE_CHANGED);
				}
			}
		}

		if (isVisible()) {
			RenderUtils.bindTexture(getTexture());
			if (overlay != null) RenderUtils.setColor(overlay);
			drawTexturedModalRect(xPos, yPos, u + (byYpos ? 0 : frame*width), v + (byYpos ? frame*height : 0),
				width, height);
		}
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isByYpos() {
		return byYpos;
	}

	public GTextureAnimated setByYpos(boolean byYpos) {
		this.byYpos = byYpos;
		return this;
	}

	public int getFrames() {
		return frames;
	}

	public int getFrame() {
		return frame;
	}

	public GTextureAnimated setFrame(int frame) {
		this.frame = MathUtils.clamp(frame, 0, frames);
		return this;
	}
}
