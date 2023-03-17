package ilib.gui.util;

/**
 * @author solo6975
 * @since 2022/4/7 14:12
 */
public interface GuiListener {
	default void keyTyped(char character, int keyCode) {}

	default void mouseDown(int mouseX, int mouseY, int button) {}

	default void mouseUp(int mouseX, int mouseY, int button) {}

	default void mouseDrag(int mouseX, int mouseY, int button, long time) {}

	default void mouseScrolled(int mouseX, int mouseY, int dir) {}
}
