package ilib.gui.util;

import ilib.gui.comp.Component;

/**
 * @author solo6975
 * @since 2022/4/3 12:09
 */
public class DragHandler extends CListenerAdapter implements GuiListener {
	public static final int DRAG = 1, RESIZE = 2;

	protected static final class DragInfo {
		int x, y;
		boolean resize;
		Component c;

		public DragInfo(Component c, int x, int y, boolean resize) {
			this.c = c;
			if (resize) {
				this.x = c.getWidth() - x;
				this.y = c.getHeight() - y;
			} else {
				this.x = c.getXPos() - x;
				this.y = c.getYPos() - y;
			}
			this.resize = resize;
		}
	}

	protected int type;
	protected DragInfo dragging;

	public DragHandler(ComponentListener parent, int type) {
		super(parent);
		this.type = type;
	}

	protected boolean isDraggable(Component c) {
		return true;
	}

	public void mouseDown(Component c, int mouseX, int mouseY, int button) {
		parent.mouseDown(c, mouseX, mouseY, button);

		if (!isDraggable(c)) return;

		boolean resize = ((type & RESIZE) != 0) && c instanceof SizeModifiable && mouseX - c.getXPos() > c.getWidth() * 0.8f && mouseY - c.getYPos() > c.getHeight() * 0.8f;

		dragging = new DragInfo(c, mouseX, mouseY, resize);
	}

	@Override
	public void mouseUp(int mouseX, int mouseY, int button) {
		DragInfo info = dragging;
		if (info != null && info.resize) {
			SizeModifiable sm = (SizeModifiable) info.c;
			sm.setWidth(Math.max(8, info.x + mouseX));
			sm.setHeight(Math.max(8, info.y + mouseY));
		}
		dragging = null;
	}

	@Override
	public void mouseDrag(int mouseX, int mouseY, int button, long time) {
		DragInfo info = dragging;
		if (info != null) {
			if (info.resize) {
				SizeModifiable sm = (SizeModifiable) info.c;
				sm.setWidth(info.x + mouseX);
				sm.setHeight(info.y + mouseY);
			} else {
				info.c.setXPos(info.x + mouseX);
				info.c.setYPos(info.y + mouseY);
			}
		}
	}
}
