package roj.gui;

import roj.concurrent.TimerTask;
import roj.concurrent.Timer;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Roj234
 * @since 2023/9/6 18:08
 */
@Deprecated
public class DragReorderHelper extends MouseAdapter {
	private TreePath clicked, insertAfter;
	private boolean dragging;
	private final JLabel el;
	private final JTree list;
	private TimerTask expandTask;
	public boolean insertMode;

	public DragReorderHelper(JTree list, JLabel displayEl) {
		this.list = list;
		list.addMouseListener(this);
		list.addMouseMotionListener(this);

		this.el = displayEl;
		this.el.setVisible(false);
	}

	@Override
	public void mousePressed(MouseEvent e) { clicked = list.getPathForLocation(e.getX(),e.getY()); }

	@Override
	public void mouseDragged(MouseEvent e) {
		if (clicked != null) {
			if (!dragging) {
				dragging = true;
				System.out.println("start drag");
				el.setVisible(true);
				el.setText(clicked.getLastPathComponent().toString());
				el.getParent().setComponentZOrder(el, 0);
				el.setBackground(Color.WHITE);
			}
			el.setBounds(new Rectangle(e.getX()+myX(list), e.getY()-20+myY(list), list.getWidth(), 20));

			TreePath next = list.getPathForLocation(e.getX(), e.getY());
			if (next != insertAfter) {
				if (expandTask != null) expandTask.cancel();
				if (next != null && next != clicked && ((TreeNode) next.getLastPathComponent()).getChildCount() > 0) {
					expandTask = Timer.getDefault().delay(() -> {
						list.expandPath(next);
					}, 800);
				}
			}

			insertAfter = next;
		}
	}

	private int myX(Component component) {
		int x = 0;
		while (!(component.getParent().getParent() instanceof JRootPane)) {
			x += component.getX();
			component = component.getParent();
		}
		return x;
	}

	private int myY(Component component) {
		int y = 0;
		while (!(component.getParent().getParent() instanceof JRootPane)) {
			y += component.getY();
			component = component.getParent();
		}
		return y;
	}

	@Override
	public void mouseExited(MouseEvent e) { mouseReleased(e); }
	@Override
	public void mouseReleased(MouseEvent e) {
		if (expandTask != null) expandTask.cancel();

		if (dragging) {
			DefaultTreeModel model = ((DefaultTreeModel) list.getModel());
			if (insertAfter != null && insertAfter != clicked) {
				MutableTreeNode obj = (MutableTreeNode) clicked.getLastPathComponent();
				MutableTreeNode parent = (MutableTreeNode) insertAfter.getPathComponent(insertAfter.getPathCount() - 2);
				MutableTreeNode after = (MutableTreeNode) insertAfter.getLastPathComponent();

				model.removeNodeFromParent(obj);
				if (!insertMode) {
					model.insertNodeInto(obj, parent, parent.getIndex(after));
				} else {
					model.insertNodeInto(obj, after, after.getChildCount());
				}

				System.out.println("finished, " + clicked + " insert after " + insertAfter);
			}
			dragging = false;
			el.setVisible(false);
		}
		clicked = insertAfter = null;
	}
}