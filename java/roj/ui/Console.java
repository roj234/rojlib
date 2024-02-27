package roj.ui;

/**
 * @author Roj234
 * @since 2023/11/19 0019 2:30
 */
public interface Console {
	default void registered() {}
	default void unregistered() {}
	default void idleCallback() {}
	void keyEnter(int keyCode, boolean isVirtualKey);
}