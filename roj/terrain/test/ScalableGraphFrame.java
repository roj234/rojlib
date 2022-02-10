package roj.terrain.test;

import roj.math.MathUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

import static roj.terrain.test.Test.GRAPH_SIZE;
import static roj.terrain.test.Test.WINDOW_SIZE;

/**
 * Frame with graph image. Image can be scaled and moved.
 */
class ScalableGraphFrame extends JFrame {
    private final class ScaleAdapter extends MouseAdapter implements KeyListener {
        private final float zoomMin, zoomMax;

        private int oldX = -1, oldY = -1;

        ScaleAdapter(float zoomMin, float zoomMax) {
            this.zoomMin = zoomMin;
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
            if (wheel == 0 || (zoom <= zoomMin && wheel > 0) ||
                    (zoom >= zoomMax && wheel < 0)) return;
            float oldW = (getWidth() / zoom);
            float oldH = (getHeight() / zoom);

            double d = Math.pow(2d, -wheel);
            zoom = (float) MathUtils.clamp(zoom * d, zoomMin, zoomMax);
            System.out.println(zoom);

            float centerX = offX + 0.5f * oldW * GRAPH_SIZE / WINDOW_SIZE;
            float centerY = offY + 0.5f * oldH * GRAPH_SIZE / WINDOW_SIZE;

            float newW = (getWidth() / zoom);
            float newH = (getHeight() / zoom);

            int bounds = (int) (imageW * zoom) - getWidth();
            offX = (int) MathUtils.clamp((centerX - 0.5f * newW), 0, bounds);
            bounds = (int) (imageH * zoom) - getHeight();
            offY = (int) MathUtils.clamp((centerY - 0.5f * newH), 0, bounds);

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

    /**
     * @param img Graph image.
     */
    ScalableGraphFrame(BufferedImage img) {
        this(img, Math.max((float) WINDOW_SIZE / img.getWidth(), (float) WINDOW_SIZE / img.getHeight()),5);

        setTitle("TDS —— 基于图的地形生成");
        setSize(WINDOW_SIZE, WINDOW_SIZE);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setResizable(true);
    }

    public ScalableGraphFrame(Image image, float zoomMin, float zoomMax) {
        this.img = image;
        this.imageW = image.getWidth(null);
        this.imageH = image.getHeight(null);

        ScaleAdapter adapter = this.adapter = new ScaleAdapter(zoomMin, zoomMax);
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        addMouseWheelListener(adapter);
        addKeyListener(adapter);
    }

    @Override
    public void paint(Graphics g) {
        g.drawImage(
                img,
                getInsets().left - offX,
                getInsets().top - offY,
                (int)(imageW * zoom),
                (int)(imageH * zoom),
                null);
    }
}
