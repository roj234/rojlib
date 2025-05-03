package roj.gui;

import roj.text.TextUtil;
import roj.util.HighResolutionTimer;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Comparator;

/**
 * @author Roj234
 * @since 2023/9/13 16:51
 */
public final class Profiler {
	private static final ThreadLocal<Profiler> LOCAL = new ThreadLocal<>();

	private static final class Node extends TreeNodeImpl<Node> {
		String name;
		long timeEnter, timeExit;

		public Node(String name) { this.name = name; this.timeEnter = System.nanoTime(); }

		@Override
		public String toString() { return name+"("+TextUtil.toFixed((time()/1_000_000d), 3)+"ms)"; }

		public void sort(Comparator<Node> cmp) {
			if (children == null) return;

			children.sort(cmp);
			for (int i = 0; i < children.size(); i++) {
				children.get(i).sort(cmp);
			}
		}

		public long time() { return timeExit-timeEnter; }
	}

	public Profiler(String name) { this.name = name; end(); }

	private final String name;
	private Node root, current, result;

	public static void endStartSection(String name) { endSection(); startSection(name); }
	public static void startSection(String name) {
		Profiler p = LOCAL.get();
		if (p == null) return;

		Node node = new Node(name);
		p.current.insert(node, p.current.getChildCount());
		p.current = node;
	}
	public static void endSection() {
		Profiler p = LOCAL.get();
		if (p == null) return;

		p.current.timeExit = System.nanoTime();
		p.current = p.current.parent;
	}

	public Profiler begin() { LOCAL.set(this); HighResolutionTimer.activate(); return this; }
	public Profiler end() {
		LOCAL.remove();
		Node r = root;
		while (current != r) {
			current.timeExit = System.nanoTime();
			current = current.parent;
		}
		current = root = new Node(name);

		if (r != null)
			r.timeExit = r.getChildCount() == 0 ? r.timeEnter : r.children.get(r.children.size()-1).timeExit;
		result = r;
		return this;
	}

	public final void popup() {
		if (LOCAL.get() == this) end();
		Popup popup = new Popup(null, result);
		popup.show();
	}

	public static class Popup extends JDialog {
		Node node;

		public Popup(Window owner, Node node) {
			super(owner);
			this.node = node;
			initComponents();
		}

		private void initComponents() {
			setTitle("Profile result");
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);

			Container root = getContentPane();
			root.setLayout(null);
			root.setPreferredSize(new Dimension(400, 485));

			DefaultTreeModel model = new DefaultTreeModel(node);
			JTree viewer = new JTree(model);
			viewer.setCellRenderer(new DefaultTreeCellRenderer() {
				{ setBackground(null); }

				final int MAX_WIDTH = 399;
				final Color BG = new Color(194, 194, 194);
				final Color FG = new Color(255, 92, 24);

				float percent;

				@Override
				public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
					super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
					Node v = (Node) value;
					percent = v.parent == null ? 1 : (float) ((double) v.time() / v.parent.time());
					return this;
				}

				@Override
				public void paint(Graphics g) {
					int width = (int) (percent*MAX_WIDTH);

					Shape clip = g.getClip();
					g.setClip(null);

					g.setColor(BG);
					g.fillRect(-getX() + width, 0, MAX_WIDTH-width, getHeight());
					g.setColor(FG);
					g.fillRect(-getX(), 0, width, getHeight());

					g.setClip(clip);
					paintComponent(g);
				}
			});

			JScrollPane jsp = new JScrollPane(viewer);
			jsp.setBounds(0, 0, 400, 455);
			root.add(jsp);

			JButton sortByTime = new JButton();
			sortByTime.setText("sort by time consumption");
			sortByTime.setMargin(new Insets(2, 4, 2, 4));
			sortByTime.setBounds(new Rectangle(new Point(120, 460), sortByTime.getPreferredSize()));
			sortByTime.addActionListener((e) -> {
				node.sort((o1, o2) -> Long.compare(o2.time(), o1.time()));
				model.reload();
				sortByTime.setEnabled(false);
			});

			root.add(sortByTime);

			pack();
			setLocationRelativeTo(getOwner());
		}
	}
}