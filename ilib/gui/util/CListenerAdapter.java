package ilib.gui.util;

import ilib.gui.comp.Component;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/7 14:24
 */
public abstract class CListenerAdapter implements ComponentListener {
	protected final ComponentListener parent;

	public CListenerAdapter(ComponentListener parent) {
		this.parent = parent;
	}

	public void componentInit(Component c) {
		parent.componentInit(c);
	}

	public void actionPerformed(Component c, int action) {
		parent.actionPerformed(c, action);
	}

	public void mouseDown(Component c, int mouseX, int mouseY, int button) {
		parent.mouseDown(c, mouseX, mouseY, button);
	}

	public void mouseUp(Component c, int mouseX, int mouseY, int button) {
		parent.mouseUp(c, mouseX, mouseY, button);
	}

	public void mouseDrag(Component c, int mouseX, int mouseY, int button, long time) {
		parent.mouseDrag(c, mouseX, mouseY, button, time);
	}

	public void mouseScrolled(Component c, int mouseX, int mouseY, int dir) {
		parent.mouseScrolled(c, mouseX, mouseY, dir);
	}

	public void getDynamicTooltip(Component c, List<String> tooltip, int mouseX, int mouseY) {
		parent.getDynamicTooltip(c, tooltip, mouseX, mouseY);
	}

	public ComponentListener getParent() {
		return parent;
	}
}
