package roj.ui;

import java.awt.*;
import java.awt.event.*;

/**
 * @author Roj234
 * @since 2023/9/7 0007 22:54
 */
public class CloseOnEsc extends KeyAdapter {
	private static final CloseOnEsc INST = new CloseOnEsc();

	public static void apply(Component com) {
		com.addKeyListener(INST);
		if (com instanceof Container) {
			Container c = (Container) com;
			for (Component c1 : c.getComponents()) {
				/* 你听到GC的哀嚎了吗 */
				apply(c1);
			}
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
			Component c = e.getComponent();
			if (!(c instanceof Window)) {
				c.dispatchEvent(new FocusEvent(c, FocusEvent.FOCUS_LOST));
			} else {
				c.hide();
			}
		}
	}
}
