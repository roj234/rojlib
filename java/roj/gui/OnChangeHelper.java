package roj.gui;

import roj.collect.SimpleList;
import roj.util.Helpers;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.IdentityHashMap;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/9/6 21:23
 */
public class OnChangeHelper extends MouseAdapter implements FocusListener, KeyListener {
	public OnChangeHelper(Component frame) {
		addRoot(frame);
	}

	public void addRoot(Component frame) {
		frame.addKeyListener(this);
		frame.addMouseListener(this);
		frame.addMouseMotionListener(this);
		frame.addFocusListener(this);
		if (frame instanceof Container) {
			Container c = (Container) frame;
			synchronized (c.getTreeLock()) {
				for (int i = 0; i < c.getComponentCount(); i++) {
					addRoot(c.getComponent(i));
				}
			}
		}
	}

	private Component focused;
	private final IdentityHashMap<Component, State> handlers = new IdentityHashMap<>();
	private boolean enabled = true;

	public void setEnabled(boolean b) {
		enabled = b;
	}

	private class State extends MouseAdapter implements DocumentListener {
		final Component self;
		final SimpleList<Consumer<Component>> change = new SimpleList<>();
		private boolean changed;

		private State(Component self) {this.self = self;}

		public void dispatch(Component o) {
			if (!enabled) return;
			if (!changed) return;
			changed = false;

			for (int i = 0; i < change.size(); i++) change.get(i).accept(self);
		}

		public void mouseClicked(MouseEvent e) { changed = true; }
		public void insertUpdate(DocumentEvent e) { changedUpdate(e); }
		public void removeUpdate(DocumentEvent e) { changedUpdate(e); }
		public void changedUpdate(DocumentEvent e) { changed = true; if (focused != self) dispatch(null); }
	}

	public <T extends Component> void addEventListener(T c, Consumer<T> handler) {
		State state = handlers.get(c);
		if (state == null) {
			handlers.put(c, state = new State(c));
			if (c instanceof JTextComponent) {
				((JTextComponent) c).getDocument().addDocumentListener(state);
			} else {
				c.addMouseListener(state);
			}
		}

		if (!state.change.contains(handler))
			state.change.add(Helpers.cast(handler));
	}

	public <T extends Component> boolean removeEventListener(T c, Consumer<T> handler) {
		State state = handlers.get(c);
		if (state == null) return false;
		if (!state.change.remove(handler)) return false;

		if (state.change.isEmpty()) {
			if (c instanceof JTextComponent)
				((JTextComponent) c).getDocument().removeDocumentListener(state);
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

	@Override
	public void keyTyped(KeyEvent e) {}
	@Override
	public void keyPressed(KeyEvent e) {
		boolean isEnter = e.getKeyCode() == KeyEvent.VK_ENTER &&
			(e.getComponent() instanceof JTextField || e.getComponent() instanceof JPasswordField);

		if (e.getKeyCode() == KeyEvent.VK_ESCAPE || isEnter) {
			Component c = e.getComponent();
			if (!(c instanceof Window)) {
				if (isEnter) {
					State s = handlers.get(c);
					if (s != null) s.dispatch(c);
				}
				c.dispatchEvent(new FocusEvent(c, FocusEvent.FOCUS_LOST));
			} else {
				c.hide();
			}
		}
	}
	@Override
	public void keyReleased(KeyEvent e) {}
}
