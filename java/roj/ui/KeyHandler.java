package roj.ui;

/**
 * @author Roj234
 * @since 2023/11/19 2:30
 */
public interface KeyHandler {
	default void registered() {}
	default void unregistered() {}
	void keyEnter(int keyCode, boolean isVirtualKey);
	default void render() {}
}