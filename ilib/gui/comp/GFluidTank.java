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
import ilib.gui.util.Sprite;
import ilib.util.MCTexts;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.GlStateManager;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GFluidTank extends SimpleComponent {
    protected int u, v;
    protected Direction direction;
    protected FluidTank tank;

    public GFluidTank(IGui parent, int x, int y, int w, int h, FluidTank tank) {
        super(parent, x, y, w, h);
        this.u = -1;

        this.tank = tank;
        this.direction = Direction.UP;
    }

    public GFluidTank(IGui parent, int x, int y, int u, int v, int w, int h, FluidTank tank) {
        super(parent, x, y, w, h);
        this.u = u;
        this.v = v;

        this.tank = tank;
        this.direction = Direction.UP;
    }

    public GFluidTank(Component relativeTo, Sprite sprite, FluidTank tank) {
        super(relativeTo.owner,
              relativeTo.xPos + sprite.offsetX(),
              relativeTo.yPos + sprite.offsetY(),
              sprite.w(), sprite.h());
        this.u = sprite.u();
        this.v = sprite.v();
        setTexture(sprite.texture());

        this.tank = tank;
        this.direction = Direction.UP;
    }

    public GFluidTank(IGui parent, int x, int y, Sprite sprite, FluidTank tank) {
        super(parent, x, y, sprite.w(), sprite.h());
        this.u = sprite.u();
        this.v = sprite.v();
        setTexture(sprite.texture());

        this.tank = tank;
        this.direction = Direction.UP;
    }

    /*******************************************************************************************************************
     * Overrides                                                                                                       *
     *******************************************************************************************************************/

    @Override
    public void render2(int mouseX, int mouseY) {
        GL11.glPushMatrix();
        GL11.glTranslatef(xPos, yPos, 0);

        int x = 0, y = 0, w = width, h = height;
        switch (direction) {
            case UP:
                break;
            case RIGHT:
                y = -w;
                w = h;
                h = width;
                GL11.glRotatef(90, 0, 0, 1);
                break;
            case LEFT:
                x = -h;
                w = h;
                h = width;
                GL11.glRotatef(-90, 0, 0, 1);
                break;
            case DOWN:
                x = -w;
                y = -h;
                GL11.glRotatef(180, 0, 0, 1);
                break;
        }

        GlStateManager.enableBlend();
        RenderUtils.renderFluid(tank, x, y + h, w, h);

        GL11.glPopMatrix();

        if (u > 0) {
            RenderUtils.bindTexture(getTexture());
            RenderUtils.colorWhite();
            drawTexturedModalRect(xPos, yPos, u, v, width, height);
        }
    }

    @Override
    public void getDynamicTooltip(List<String> tooltip, int mouseX, int mouseY) {
        super.getDynamicTooltip(tooltip, mouseX, mouseY);

        FluidStack stack = tank.getFluid();
        if (stack == null || stack.amount == 0) {
            tooltip.add(MCTexts.format("tooltip.empty"));
        } else {
            tooltip.add(stack.getFluid().getLocalizedName(stack));
            tooltip.add(tank.getFluidAmount() + " / " + tank.getCapacity() + "mb");
        }
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

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public FluidTank getTank() {
        return tank;
    }

    public void setTank(FluidTank tank) {
        this.tank = tank;
    }
}
