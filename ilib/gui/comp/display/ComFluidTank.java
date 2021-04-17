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
import ilib.gui.comp.BaseComponent;
import ilib.util.TextHelperM;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.util.List;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ComFluidTank extends BaseComponent {
    // Variables
    protected int width, height;
    protected int u, v;
    protected FluidTank tank;

    /**
     * Creates a fluid tank renderer
     *
     * @param parent    The parent GUI
     * @param x         The x pos
     * @param y         The y pos
     * @param w         The width
     * @param h         The height
     * @param fluidTank The fluid tank, has fluid to render
     */
    public ComFluidTank(IGui parent, int x, int y, int w, int h, FluidTank fluidTank) {
        super(parent, x, y);
        this.width = w;
        this.height = h;
        this.tank = fluidTank;
        this.u = this.v = -1;
    }

    public ComFluidTank(IGui parent, int x, int y, int u, int v, int w, int h, FluidTank tank) {
        this(parent, x, y, w, h, tank);
        this.u = u;
        this.v = v;
    }

    /*******************************************************************************************************************
     * BaseComponent                                                                                                   *
     *******************************************************************************************************************/

    /**
     * Called to render the component
     */
    @Override
    public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        // No Op
    }

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    @Override
    public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        //GlStateManager.pushMatrix();
        //GlStateManager.translate(xPos, yPos, 0);
        RenderUtils.renderFluid(tank, xPos, yPos + height, height, width);
        RenderUtils.bindTexture(getTexture());
        if (u >= 0) {
            drawTexturedModalRect(xPos, yPos, u, v, width, height);
        }
        //GlStateManager.popMatrix();
    }

    @Override
    public void getDynamicToolTip(List<String> toolTip, int mouseX, int mouseY) {
        FluidStack stack = tank.getFluid();
        if (stack == null || stack.amount == 0) {
            toolTip.add(TextHelperM.translate("tooltip.empty"));
        } else {
            toolTip.add(stack.getFluid().getLocalizedName(stack));
            toolTip.add(tank.getFluidAmount() + " / " + tank.getCapacity() + "mb");
        }
    }

    /**
     * Used to find how wide this is
     *
     * @return How wide the component is
     */
    @Override
    public int getWidth() {
        return width;
    }

    /**
     * Used to find how tall this is
     *
     * @return How tall the component is
     */
    @Override
    public int getHeight() {
        return height;
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public FluidTank getTank() {
        return tank;
    }

    public void setTank(FluidTank tank) {
        this.tank = tank;
    }
}
