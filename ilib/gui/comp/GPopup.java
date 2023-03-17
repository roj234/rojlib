package ilib.gui.comp;

import ilib.gui.IGui;
import ilib.gui.util.ComponentListener;
import ilib.gui.util.PositionProxy;

import java.awt.*;
import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/1 23:01
 */
public class GPopup extends GGroup {
	public static final int CLOSE_BTN = 999;
	public static final Color DEFAULT_BACKGROUND = new Color(0x888888);

	public GPopup(IGui parent, String title, String content, List<String> buttons, int markBegin) {
		super(parent, 0, 0, 200, 160);
		listener = null;

		ComponentListener lst = (ComponentListener) parent;
		components.add(new GColoredQuad(this, 0, 0, 0, 0, DEFAULT_BACKGROUND).setZIndex(0));
		components.add(new GText(this, 4, 4, title, Color.WHITE));
		components.add(new GTextLong(this, 4, 16, -8, -48, content, 100).setListener(lst));
		components.add(new GButtonNP(this, -4, 4, 12, 12, "X").setMark(CLOSE_BTN).setListener(lst));

		markBegin--;
		PositionProxy pp = new PositionProxy(lst, PositionProxy.POSITION_FLEX_X);
		pp.setMarginBegin(6);
		pp.setMarginComponent(6);
		pp.setMarginEnd(6);
		for (int i = 1; i <= buttons.size(); i++) {
			pp.position(i + markBegin, 0, -24);
			components.add(new GButtonNP(this, 0, 0, 0, 20, buttons.get(i - 1)).setMark(i + markBegin).setListener(pp));
		}
	}

	@Override
	public void onInit() {
		superInit();

		int w = this.width;
		int h = this.height;

		xPos = (owner.getWidth() - w) / 2;
		yPos = (owner.getHeight() - h) / 2;

		SimpleComponent bg = (SimpleComponent) components.get(0);
		bg.width = w;
		bg.height = h;

		Component title = components.get(1);
		title.xPos = (w - title.getWidth()) / 2;

		SimpleComponent content = (SimpleComponent) components.get(2);
		content.width = w - 8;
		content.height = w - 48;

		SimpleComponent close = (SimpleComponent) components.get(3);
		close.xPos = w - 16;

		for (int i = 0; i < components.size(); i++) {
			components.get(i).onInit();
		}

		if (components.size() > 4) {
			PositionProxy pp = (PositionProxy) components.get(4).getListener();
			pp.reposition(this);
		}

	}

	public void setBackgroundColor(Color c) {
		GColoredQuad bg = (GColoredQuad) components.get(0);
		bg.setColor(c);
	}

	public void setForegroundColor(Color c) {
		GText title = (GText) components.get(1);
		GTextLong content = (GTextLong) components.get(2);
		title.setColor(c);
		content.setColor(c);
	}

	public String getTitle() {
		GText title = (GText) components.get(1);
		return title.text;
	}

	public void setContent(String cnt) {
		GTextLong content = (GTextLong) components.get(2);
		content.setRawLine(cnt);
	}
}
