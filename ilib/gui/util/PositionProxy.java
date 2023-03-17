package ilib.gui.util;

import ilib.gui.IGui;
import ilib.gui.comp.Component;
import roj.collect.IntMap;
import roj.collect.SimpleList;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/3 12:09
 */
public class PositionProxy extends CListenerAdapter {
	public static final int POSITION_RELATIVE = 0;
	public static final int POSITION_PERCENT = 1;
	public static final int POSITION_FLEX_X = 2;
	public static final int POSITION_FLEX_Y = 3;

	static final class PositionInfo {
		Component component;
		int type;
		float x, y, w, h;

		PositionInfo(float x, float y, float w, float h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}
	}

	private final int type;
	private int marginBegin;
	private int marginEnd;
	private int marginComponent;
	private final IntMap<PositionInfo> position = new IntMap<>();

	public PositionProxy(ComponentListener parent, int type) {
		super(parent);
		this.type = type;
		this.marginBegin = marginComponent = marginEnd = 6;
	}

	public PositionProxy position(int mark, float x, float y, float w, float h) {
		PositionInfo info = new PositionInfo(x, y, w, h);
		position.putInt(mark, info);
		return this;
	}

	public PositionProxy position(int mark, float x, float y) {
		PositionInfo info = new PositionInfo(x, y, Float.NaN, Float.NaN);
		position.putInt(mark, info);
		return this;
	}

	public void reposition(IGui gui) {
		for (PositionInfo info : position.values()) {
			Component c = info.component;
			if (c == null) continue;

			int x = (int) info.x, y = (int) info.y, w = (int) info.w, h = (int) info.h;
			if (x < 0) x += gui.getWidth();
			if (y < 0) y += gui.getHeight();
			if (w < 0) w = gui.getWidth() + w - x;
			if (h < 0) h = gui.getHeight() + h - y;

			c.setXPos(x);
			c.setYPos(y);
			if (c instanceof SizeModifiable) {
				SizeModifiable sc = (SizeModifiable) c;
				if (info.w == info.w) sc.setWidth(w);
				if (info.h == info.h) sc.setHeight(h);
			}
		}

		switch (type) {
			case POSITION_RELATIVE:
				break;
			case POSITION_PERCENT: {
				for (PositionInfo info : position.values()) {
					Component c = info.component;
					if (c == null) continue;

					c.setXPos((int) (info.x <= 1 ? gui.getLeft() * info.x : info.x));
					c.setYPos((int) (info.y <= 1 ? gui.getTop() * info.y : info.y));
					if (c instanceof SizeModifiable) {
						SizeModifiable sc = (SizeModifiable) c;
						sc.setWidth((int) (info.w <= 1 ? gui.getLeft() * info.w : info.w));
						sc.setHeight((int) (info.h <= 1 ? gui.getLeft() * info.h : info.h));
					}
				}
			}
			break;
			case POSITION_FLEX_X: {
				IntMap<List<Component>> by = new IntMap<>();
				for (PositionInfo info : position.values()) {
					Component c = info.component;
					if (c == null) continue;

					List<Component> list = by.get(c.getYPos());
					if (list == null) by.putInt(c.getYPos(), list = new SimpleList<>(4));
					list.add(c);
				}

				for (List<Component> list : by.values()) {
					int totalWidth = marginBegin + marginComponent * (list.size() - 1) + marginEnd;

					for (int i = 0; i < list.size(); i++) {
						totalWidth += list.get(i).getWidth();
					}

					int left = (gui.getWidth() - totalWidth) / 2;

					for (int i = 0; i < list.size(); i++) {
						Component c = list.get(i);
						c.setXPos(left);

						left += c.getWidth() + marginComponent;
					}
				}
			}
			break;
			case POSITION_FLEX_Y: {
				IntMap<List<Component>> by = new IntMap<>();
				for (PositionInfo info : position.values()) {
					Component c = info.component;
					if (c == null) continue;

					List<Component> list = by.get(c.getXPos());
					if (list == null) by.putInt(c.getXPos(), list = new SimpleList<>(4));
					list.add(c);
				}

				for (List<Component> list : by.values()) {
					int totalHeight = marginBegin + marginComponent * (list.size() - 1) + marginEnd;

					for (int i = 0; i < list.size(); i++) {
						totalHeight += list.get(i).getHeight();
					}

					int top = (gui.getHeight() - totalHeight) / 2;

					for (int i = 0; i < list.size(); i++) {
						Component c = list.get(i);
						c.setYPos(top);

						top += c.getHeight() + marginComponent;
					}
				}
			}
			break;
		}
	}

	public void componentInit(Component c) {
		parent.componentInit(c);

		PositionInfo info = position.get(c.getMark());
		if (info != null) info.component = c;
	}

	public void setMarginBegin(int marginBegin) {
		this.marginBegin = marginBegin;
	}

	public int getMarginBegin() {
		return marginBegin;
	}

	public void setMarginEnd(int marginEnd) {
		this.marginEnd = marginEnd;
	}

	public int getMarginEnd() {
		return marginEnd;
	}

	public void setMarginComponent(int marginComponent) {
		this.marginComponent = marginComponent;
	}

	public int getMarginComponent() {
		return marginComponent;
	}
}
