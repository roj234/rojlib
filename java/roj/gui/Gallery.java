package roj.gui;

import roj.math.MathUtils;

import java.awt.*;
import java.awt.event.*;

/**
 * Image can be scaled and moved.
 */
public class Gallery extends Container {
	private final class ScaleAdapter extends MouseAdapter implements KeyListener {
		private final float zoomMax;

		private int oldX = -1, oldY = -1;

		ScaleAdapter(float zoomMax) {
			this.zoomMax = zoomMax;
		}

		@Override
		public void mousePressed(MouseEvent e) {
			oldX = e.getX();
			oldY = e.getY();
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			oldX = -1;
			oldY = -1;
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			if (oldX != -1) {
				int dx = e.getX() - oldX;
				int dy = e.getY() - oldY;

				int bounds = (int) (imageW * zoom) - getWidth();
				offX = MathUtils.clamp(offX - dx, 0, bounds);
				bounds = (int) (imageH * zoom) - getHeight();
				offY = MathUtils.clamp(offY - dy, 0, bounds);

				oldX = e.getX();
				oldY = e.getY();

				repaint();
			}
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			onWheel(e.getWheelRotation());
		}

		private void onWheel(int wheel) {
			final float zoomMin = Math.max((float) getHeight()/imageH, (float) getWidth()/imageW);

			if (wheel == 0 || (zoom <= zoomMin && wheel > 0) || (zoom >= zoomMax && wheel < 0)) return;

			double d = Math.pow(2d, -wheel);
			float r1 = (float) MathUtils.clamp(zoom * d, zoomMin, zoomMax);

			float r = zoom;
			float w = imageW * r;
			float h = imageH * r;

			float dx, dy;
			dx = -offX - (getWidth() - w) / 2;
			dy = -offY - (getHeight() - h) / 2;

			zoom = r1;

			w = imageW * r1;
			h = imageH * r1;

			// 基于中心点的缩放居然这么简单
			r1 /= r;
			offX = -(int) ((getWidth() - w) / 2 + dx * r1);
			offY = -(int) ((getHeight() - h) / 2 + dy * r1);

			int bounds = (int) (imageW * zoom) - getWidth();
			offX = MathUtils.clamp(offX, 0, bounds);
			bounds = (int) (imageH * zoom) - getHeight();
			offY = MathUtils.clamp(offY, 0, bounds);

			repaint();
		}

		@Override
		public void keyTyped(KeyEvent e) {
			// CTRL 0-+
			if (e.isControlDown()) {
				switch (e.getKeyChar()) {
					case '+':
						onWheel(2);
						break;
					case '-':
						onWheel(-2);
						break;
					case '0':
						if (zoom != 1) {
							zoom = 1;
							offX = offY = 0;
							repaint();
						}
						break;
				}
			}
		}

		@Override
		public void keyPressed(KeyEvent e) {}

		@Override
		public void keyReleased(KeyEvent e) {}
	}

	final ScaleAdapter adapter;
	private final Image img;
	private final int imageW, imageH;
	private int offX, offY;
	private float zoom = 1;

	public Gallery(Image img) {
		this(img, 8);
	}

	public Gallery(Image image, float zoomMax) {
		this.img = image;
		this.imageW = image.getWidth(null);
		this.imageH = image.getHeight(null);

		ScaleAdapter adapter = this.adapter = new ScaleAdapter(zoomMax);
		addMouseListener(adapter);
		addMouseMotionListener(adapter);
		addMouseWheelListener(adapter);
		addKeyListener(adapter);
	}

	@Override
	public void paint(Graphics g) {
		Insets insets = getInsets();
		g.drawImage(img, insets.left - offX, insets.top - offY, (int) (imageW * zoom), (int) (imageH * zoom), null);
	}
}
