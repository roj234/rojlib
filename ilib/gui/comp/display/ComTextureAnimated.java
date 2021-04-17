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

package ilib.gui.comp.display;

import ilib.client.util.RenderUtils;
import ilib.gui.IGui;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/13 12:37
 */
public abstract class ComTextureAnimated extends ComTexture {
    protected Direction direction;

    /**
     * Tells the component which way to render the texture
     */
    public enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    public ComTextureAnimated(IGui parent, int x, int y, int texU, int texV, int imageWidth, int imageHeight, Direction dir) {
        super(parent, x, y, texU, texV, imageWidth, imageHeight);
        this.direction = dir;
    }


    /**
     * Get the current scale, scaled to the width
     *
     * @param scale What to scale to
     * @return How far along 0-scale in current animation
     */
    protected abstract int getProgress(int scale);

    /*******************************************************************************************************************
     * BaseComponent                                                                                                   *
     *******************************************************************************************************************/

    @Override
    public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        RenderUtils.bindTexture(getTexture());

        switch (direction) {
            case RIGHT:
                int progressRight = Math.min(width, getProgress(width));
                drawTexturedModalRect(xPos, yPos, u, v, progressRight, height);
                break;
            case DOWN:
                int progressDown = Math.min(height, getProgress(height));
                drawTexturedModalRect(xPos, yPos, u, v, width, progressDown);
                break;
            case LEFT:
                int progressLeft = Math.min(width, getProgress(width));
                drawTexturedModalRect(xPos + -width + progressLeft, yPos, u, v, progressLeft, height);
                break;
            case UP:
                int progressUp = Math.min(height, getProgress(height));
                drawTexturedModalRect(xPos, height - progressUp + yPos, u, v + height - progressUp, width, progressUp);
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
}
