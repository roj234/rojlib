package ilib.gui.util;

import ilib.gui.comp.Component;

import java.util.List;

public interface ComponentListener {
	int BUTTON_CLICKED = 0, TEXT_CHANGED = 1, SLIDER_MOVED = 2, ANIMATE_TEXTURE_CHANGED = 3;

	default void componentInit(Component c) {}

	default void actionPerformed(Component c, int action) {}

	default void mouseDown(Component c, int mouseX, int mouseY, int button) {}

	default void mouseUp(Component c, int mouseX, int mouseY, int button) {}

	default void mouseDrag(Component c, int mouseX, int mouseY, int button, long time) {}

	default void mouseScrolled(Component c, int mouseX, int mouseY, int dir) {}

	default void getDynamicTooltip(Component c, List<String> tooltip, int mouseX, int mouseY) {}
}
