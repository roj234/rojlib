package roj.gui;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/9/14 21:53
 */
public class DoubleClickHelper extends MouseAdapter {
	private final JList<?> component;
	private final int delay;
	private final Consumer<JList<?>> callback;

	long prevClick;
	int prevId;

	public DoubleClickHelper(JList<?> component, int delay, Consumer<JList<?>> callback) {
		this.component = component;
		this.delay = delay;
		this.callback = callback;
	}

	@Override
	public void mousePressed(MouseEvent e) {
		int[] pp = component.getSelectedIndices();
		if (pp.length != 1) {
			prevId = -1;
			return;
		}

		if (pp[0] == prevId && System.currentTimeMillis() - prevClick < delay) {
			callback.accept(component);
			return;
		}

		prevId = pp[0];
		prevClick = System.currentTimeMillis();
	}
}