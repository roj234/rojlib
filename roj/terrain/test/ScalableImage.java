/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.terrain.test;

import roj.math.MathUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import static roj.terrain.test.Test.GRAPH_SIZE;
import static roj.terrain.test.Test.WINDOW_SIZE;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/13 12:57
 */
public class ScalableImage extends JComponent {
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

                ScalableImage.this.repaint();
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

            ScalableImage.this.repaint();
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
                            ScalableImage.this.repaint();
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

    public ScalableImage(Image image, float zoomMin, float zoomMax) {
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
    protected void paintComponent(Graphics g) {
        g.drawImage(
                img,
                getInsets().left - offX,
                getInsets().top - offY,
                (int)(imageW * zoom),
                (int)(imageH * zoom),
                null);
    }
}
