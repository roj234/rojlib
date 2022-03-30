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
import ilib.gui.util.Direction;

/**
 * @author Roj234
 * @since 2021/1/13 12:37
 */
public class GProgressBar extends GTexture {
    protected Direction direction;
    protected Provider provider;

    public GProgressBar(IGui parent, int x, int y, int u, int v, int w, int h, Direction dir) {
        super(parent, x, y, u, v, w, h);
        this.direction = dir;
    }

    /**
     * @param length What to scale to
     */
    protected int getProgress(int length) {
        return provider.getProgress(this, length);
    }

    /*******************************************************************************************************************
     * Overrides                                                                                                       *
     *******************************************************************************************************************/

    @Override
    public void render(int mouseX, int mouseY) {
        RenderUtils.bindTexture(getTexture());

        int p;
        switch (direction) {
            case UP:
                p = Math.min(height, getProgress(height));
                drawTexturedModalRect(xPos, height - p + yPos, u, v + height - p, width, p);
                break;
            case DOWN:
                p = Math.min(height, getProgress(height));
                drawTexturedModalRect(xPos, yPos, u, v, width, p);
                break;
            case LEFT:
                p = Math.min(width, getProgress(width));
                drawTexturedModalRect(xPos + -width + p, yPos, u, v, p, height);
                break;
            case RIGHT:
                p = Math.min(width, getProgress(width));
                drawTexturedModalRect(xPos, yPos, u, v, p, height);
                break;
        }
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public Provider getProvider() {
        return provider;
    }

    public GProgressBar setProvider(Provider provider) {
        this.provider = provider;
        return this;
    }

    public interface Provider {
        int getProgress(GProgressBar sel, int length);
    }
}
