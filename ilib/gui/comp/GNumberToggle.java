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
import ilib.gui.GuiHelper;
import ilib.gui.IGui;

import net.minecraft.client.renderer.GlStateManager;

/**
 * 以后用到了在重构
 *
 * @author Roj234
 * @since 2021/1/13 12:37
 */
@Deprecated
public abstract class GNumberToggle extends GNumberInput {
    // Variables
    protected int u, v;
    protected boolean upSelected, downSelected = false;

    /**
     * Creates the set number object
     * <p>
     * IMPORTANT: You must create the up and down arrow and pass the u and v or the top left corner
     * It should look like the following in the texture sheet:
     * UN|US
     * DN|DS
     * <p>
     * With UN and DN being the normal up and down and US and DS being the selected versions (when clicked)
     * The arrow buttons should be 11x8 pixels and all touching to form one big rectangle
     */
    public GNumberToggle(IGui parent, int x, int y, int height, int width, int texU, int texV, int value, int lowestValue, int highestValue) {
        super(parent, x, y, width, height, value, lowestValue, highestValue);
        this.u = texU;
        this.v = texV;
    }

    /*******************************************************************************************************************
     * Overrides                                                                                                       *
     *******************************************************************************************************************/

    @Override
    public void mouseDown(int x, int y, int button) {
        if (GuiHelper.isInBounds(x, y, xPos + width - 8, yPos - 1, xPos + width + 2, yPos + 7)) {
            upSelected = true;

            if (val < max)
                val += 1;

            GuiHelper.playButtonSound();
            onChange(val);
            setText(String.valueOf(val));
        } else if (GuiHelper.isInBounds(x, y, xPos + width - 8, yPos + 9, xPos + width + 2, yPos + 17)) {
            downSelected = true;

            if (val > min)
                val -= 1;

            GuiHelper.playButtonSound();
            onChange(val);
            setText(String.valueOf(val));
        }
        super.mouseDown(x, y, button);
    }

    @Override
    public void mouseUp(int x, int y, int button) {
        super.mouseUp(x, y, button);
        upSelected = downSelected = false;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(xPos, yPos, 0);
        RenderUtils.bindTexture(getTexture());
        drawTexturedModalRect(width - 1, -1, upSelected ? u + 11 : u, v, 11, 8);
        drawTexturedModalRect(width - 8, 9, downSelected ? u + 11 : u, v + 8, 11, 9);
        GlStateManager.popMatrix();
        super.render(mouseX, mouseY);
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
}
