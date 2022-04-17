/*
 * This file is a part of MI
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

package ilib.gui.comp;

import ilib.client.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.util.Sprite;

import java.awt.*;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GTexture extends SimpleComponent {
    protected int u, v;
    protected Color overlay;

    public GTexture(IGui parent, int x, int y, int u, int v, int w, int h) {
        super(parent, x, y, w, h);
        this.u = u;
        this.v = v;
    }

    public GTexture(Component relativeTo, Sprite sprite) {
        super(relativeTo.owner,
              relativeTo.xPos + sprite.offsetX(),
              relativeTo.yPos + sprite.offsetY(),
              sprite.w(), sprite.h());
        u = sprite.u();
        v = sprite.v();
        setTexture(sprite.texture());
    }

    public GTexture(IGui parent, int x, int y, Sprite sprite) {
        super(parent, x, y, sprite.w(), sprite.h());
        u = sprite.u();
        v = sprite.v();
        setTexture(sprite.texture());
    }

    /*******************************************************************************************************************
     * Overrides                                                                                                       *
     *******************************************************************************************************************/

    @Override
    public void render(int mouseX, int mouseY) {
        if (isVisible()) {
            RenderUtils.bindTexture(getTexture());
            if (overlay != null) RenderUtils.setColor(overlay);
            drawTexturedModalRect(xPos, yPos, u, v, width, height);
        }
    }

    protected boolean isVisible() {
        return true;
    }
    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public int getU() {
        return u;
    }

    public void setU(int u) {
        this.u = u;
    }

    public int getV() {
        return v;
    }

    public void setV(int v) {
        this.v = v;
    }

    public Color getOverlay() {
        return overlay;
    }

    public GTexture setOverlay(Color overlay) {
        this.overlay = overlay;
        return this;
    }
}
