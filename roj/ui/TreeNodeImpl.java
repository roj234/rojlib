package roj.ui;

import roj.collect.SimpleList;

import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/9/14 0014 20:57
 */
public abstract class TreeNodeImpl<T extends TreeNodeImpl<T>> implements MutableTreeNode {
	public transient T parent;
	public List<T> children;

	public final T getParent() { return parent; }
	@SuppressWarnings("unchecked")
	public void setParent(MutableTreeNode newParent) { this.parent = (T) newParent; }
	public void removeFromParent() { parent.children.remove(this); parent = null; }

	public final void setParents() {
		if (children == null) return;
		for (int i = 0; i < children.size(); i++) {
			T c = children.get(i);
			c.setParent(this);
			c.setParents();
		}
	}

	public final T getChildAt(int i) { return children.get(i); }
	public final int getChildCount() { return children == null ? 0 : children.size(); }

	public final int sumChildCount() {
		if (getChildCount() == 0) return 1;
		int val = 1;
		for (int i = 0; i < children.size(); i++) {
			val += children.get(i).sumChildCount();
		}
		return val;
	}

	public final int getIndex(TreeNode node) { return children == null ? -1 : children.indexOf(node); }

	public final boolean getAllowsChildren() { return children != null; }

	public final boolean isLeaf() { return children == null || children.isEmpty(); }
	public final Enumeration<?> children() { return children == null ? Collections.emptyEnumeration() : Collections.enumeration(children); }

	@SuppressWarnings("unchecked")
	public final void flat(Collection<T> out) {
		out.add((T) this);
		if (getChildCount() > 0) {
			for (int i = 0; i < children.size(); i++) {
				children.get(i).flat(out);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public final void insert(MutableTreeNode child, int index) {
		if (children == null) children = new SimpleList<>();
		T c = (T) child;
		c.setParent(this);
		children.add(index, c);
	}

	public final void remove(int index) { children.remove(index); }
	public final void remove(MutableTreeNode node) { children.remove(node); }

	public void setUserObject(Object object) { throw new UnsupportedOperationException(); }
	public abstract String toString();
}