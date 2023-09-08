package roj.ui;

import roj.collect.SimpleList;
import roj.util.Helpers;

import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.IdentityHashMap;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/9/6 0006 21:23
 */
public class TextChangeEventHelper extends MouseAdapter implements FocusListener {
	public TextChangeEventHelper(Component frame) {
		addRoot(frame);
	}

	public void addRoot(Component frame) {
		frame.addMouseListener(this);
		frame.addMouseMotionListener(this);
		frame.addFocusListener(this);
		if (frame instanceof Container) {
			Container c = (Container) frame;
			for (Component c1 : c.getComponents()) {
				/* 你听到GC的哀嚎了吗 */
				addRoot(c1);
			}
		}
	}

	private Component focused;
	private final IdentityHashMap<Component, State> handlers = new IdentityHashMap<>();

	private static class State extends MouseAdapter implements KeyListener {
		SimpleList<Consumer<Component>> callback = new SimpleList<>();
		boolean changed;

		public void keyTyped(KeyEvent e) { changed = true; }
		public void mouseClicked(MouseEvent e) { changed = true; }

		public void keyPressed(KeyEvent e) {}
		public void keyReleased(KeyEvent e) {}

		public void dispatch(Component o) {
			if (!changed) return;
			changed = false;

			for (int i = 0; i < callback.size(); i++) callback.get(i).accept(o);
		}
	}

	public <T extends Component> void addEventListener(T c, Consumer<T> handler) {
		State state = handlers.get(c);
		if (state == null) {
			handlers.put(c, state = new State());
			if (c instanceof JTextComponent) {
				c.addKeyListener(state);
			} else {
				c.addMouseListener(state);
			}
		}

		if (!state.callback.contains(handler))
			state.callback.add(Helpers.cast(handler));
	}

	public <T extends Component> boolean removeEventListener(T c, Consumer<T> handler) {
		State state = handlers.get(c);
		if (state == null) return false;
		if (!state.callback.remove(handler)) return false;

		if (state.callback.isEmpty()) {
			c.removeKeyListener(state);
			c.removeMouseListener(state);
			handlers.remove(c);
		}
		return true;
	}

	@Override
	public void mousePressed(MouseEvent e) {
		Component c = e.getComponent();

		if (focused != null && focused != c) {
			Component tc = focused;
			tc.dispatchEvent(new FocusEvent(c, FocusEvent.FOCUS_LOST));

			State obj = handlers.get(tc);
			if (obj != null) obj.dispatch(tc);
		}

		focused = c;
	}

	@Override
	public void focusGained(FocusEvent e) {
		Component c = e.getComponent();

		if (focused != null && focused != c) {
			Component tc = focused;

			State obj = handlers.get(tc);
			if (obj != null) obj.dispatch(tc);
		}

		focused = c;
	}

	@Override
	public void focusLost(FocusEvent e) { focused = null; }
}
