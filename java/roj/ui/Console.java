package roj.ui;

/**
 * @author Roj234
 * @since 2023/11/19 0019 2:30
 */
public interface Console {
	void registered();
	void unregistered();
	void keyEnter(int keyCode, boolean isVirtualKey);
}
