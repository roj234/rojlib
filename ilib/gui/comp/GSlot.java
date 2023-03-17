package ilib.gui.comp;

import ilib.gui.IGui;
import ilib.gui.util.Sprite;

import net.minecraft.inventory.Slot;

/**
 * @author Roj234
 * @since 2021/1/13 12:42
 */
public class GSlot extends GTexture {
	protected Slot slot;
	protected int shownX, shownY;
	protected boolean display = true;

	public GSlot(IGui parent, Slot slot, int slotX, int slotY, int x, int y, int u, int v, int w, int h) {
		super(parent, x, y, u, v, w, h);
		this.slot = slot;
		this.shownX = slotX;
		this.shownY = slotY;
	}

	public GSlot(IGui parent, Slot slot, int slotX, int slotY, int x, int y, Sprite bg) {
		super(parent, x - (bg.w() - 16) / 2, y - (bg.h() - 16) / 2, bg);
		this.slot = slot;
		this.shownX = slotX;
		this.shownY = slotY;
	}

	@Deprecated
	public GSlot(IGui parent, Slot slot, int x, int y, int u, int v) {
		this(parent, slot, x + 1, y + 1, x, y, u, v, 18, 18);
	}

	public void setVisible(boolean display) {
		this.display = display;
		if (display) {
			slot.xPos = shownX;
			slot.yPos = shownY;
		} else {
			slot.xPos = -10000;
			slot.yPos = -10000;
		}
	}

	@Override
	protected boolean isVisible() {
		return display;
	}

	public Slot getSlot() {
		return slot;
	}
}
