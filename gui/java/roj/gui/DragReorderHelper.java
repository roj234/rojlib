package roj.gui;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 修复了索引偏移和 Z-depth 遮挡问题的重排序助手
 */
public class DragReorderHelper extends MouseAdapter {
	private final JTree tree;
	private final JLabel dragLabel;

	private TreePath sourcePath;
	private boolean isDragging;

	private final Timer expandTimer;

	public DragReorderHelper(JTree tree, JLabel dragLabel) {
		this.tree = tree;
		this.dragLabel = dragLabel;

		this.tree.addMouseListener(this);
		this.tree.addMouseMotionListener(this);

		this.dragLabel.setVisible(false);
		this.dragLabel.setOpaque(true);
		this.dragLabel.setBackground(new Color(255, 255, 255, 200));
		this.dragLabel.setBorder(BorderFactory.createLineBorder(Color.BLUE));

		this.expandTimer = new Timer(800, e -> {
			TreePath hover = tree.getPathForLocation(lastPoint.x, lastPoint.y);
			if (hover != null) tree.expandPath(hover);
		});
		this.expandTimer.setRepeats(false);
	}

	private Point lastPoint;

	@Override
	public void mousePressed(MouseEvent e) {
		sourcePath = tree.getPathForLocation(e.getX(), e.getY());
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if (sourcePath == null) return;
		lastPoint = e.getPoint();

		if (!isDragging) {
			isDragging = true;
			dragLabel.setText(" " + sourcePath.getLastPathComponent().toString());
			dragLabel.setVisible(true);

			// 解决 Z-index 问题：将标签置于最顶层
			Container parent = dragLabel.getParent();
			if (parent != null) {
				parent.setComponentZOrder(dragLabel, 0);
				parent.repaint();
			}
		}

		// 更新预览标签位置
		Point parentPoint = SwingUtilities.convertPoint(tree, e.getPoint(), dragLabel.getParent());
		dragLabel.setBounds(parentPoint.x + 15, parentPoint.y - 10,
				dragLabel.getPreferredSize().width + 20, 25);

		expandTimer.restart();
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		expandTimer.stop();
		if (isDragging) {
			isDragging = false;
			dragLabel.setVisible(false);

			TreePath targetPath = tree.getPathForLocation(e.getX(), e.getY());
			if (targetPath != null) {
				processMove(sourcePath, targetPath, e.getPoint());
			}
		}
		sourcePath = null;
	}

	private void processMove(TreePath source, TreePath target, Point p) {
		if (source.equals(target)) return;

		DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
		MutableTreeNode sourceNode = (MutableTreeNode) source.getLastPathComponent();
		MutableTreeNode targetNode = (MutableTreeNode) target.getLastPathComponent();
		MutableTreeNode oldParent = (MutableTreeNode) sourceNode.getParent();

		if (oldParent == null || isAncestor(sourceNode, targetNode)) return;

		Rectangle bounds = tree.getPathBounds(target);
		if (bounds == null) return;

		MutableTreeNode newParent;
		int targetIndex;

		// 计算鼠标在目标节点上的相对位置
		double relativeY = p.y - bounds.y;
		double threshold = bounds.height * 0.3; // 顶部或底部 30% 用于排序，中间 40% 用于插入

		if (relativeY < threshold) {
			// 插入到目标之前
			newParent = (MutableTreeNode) targetNode.getParent();
			targetIndex = newParent.getIndex(targetNode);
		} else if (relativeY > bounds.height - threshold) {
			if (targetNode != model.getRoot()) {
				// 插入到目标之后
				newParent = (MutableTreeNode) targetNode.getParent();
				targetIndex = newParent.getIndex(targetNode) + 1;
			} else {
				newParent = targetNode;
				targetIndex = 0;
			}
		} else {
			// 核心修复：当已经是目标的子节点时，不执行插入操作
			if (isAncestor(targetNode, sourceNode)) return;

			// 放入目标内部作为子节点
			newParent = targetNode;
			targetIndex = newParent.getChildCount();
		}

		// --- 核心修复：处理索引漂移 ---
		int oldIndex = oldParent.getIndex(sourceNode);

		// 如果在同一个父节点下移动，且目标位置在原始位置之后
		// 移除操作会导致后面的节点索引全部 -1
		if (newParent == oldParent && targetIndex > oldIndex) {
			targetIndex--;
		}

		// 执行原子转换
		model.removeNodeFromParent(sourceNode);
		model.insertNodeInto(sourceNode, newParent, Math.max(0, targetIndex));

		// 选中并展开
		TreePath newPath = new TreePath(model.getPathToRoot(sourceNode));
		tree.setSelectionPath(newPath);
		tree.scrollPathToVisible(newPath);
	}

	private boolean isAncestor(TreeNode node1, TreeNode node2) {
		while (node2 != null) {
			if (node1 == node2) return true;
			node2 = node2.getParent();
		}
		return false;
	}
}
