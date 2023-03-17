package ilib.gui;

import ilib.gui.comp.Component;
import ilib.gui.comp.GPopup;
import ilib.gui.util.ComponentListener;

import net.minecraft.client.gui.GuiScreen;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/1 1:24
 */
public class GuiPopup extends GuiBaseNI implements ComponentListener {
	public GPopup popup;

	public GuiPopup(GuiScreen prev, String title, String content, List<String> buttons) {
		super(-1, -1, Component.TEXTURE);
		this.prevScreen = prev;
		this.popup = new GPopup(this, title, content, buttons, 100);
		this.popup.setListener(this);
	}

	@Override
	protected void addComponents() {
		components.add(popup);
	}

	@Override
	public void actionPerformed(Component c, int action) {
		if (action == BUTTON_CLICKED) {
			if (c.getMark() == GPopup.CLOSE_BTN) mc.displayGuiScreen(prevScreen);
		}
	}
}
