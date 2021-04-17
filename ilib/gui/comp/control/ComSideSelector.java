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

package ilib.gui.comp.control;

import ilib.ClientProxy;
import ilib.client.util.KeyHelper;
import ilib.client.util.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.comp.BaseComponent;
import ilib.gui.misc.SidePicker;
import ilib.gui.misc.TrackballMC;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import roj.util.Color;

import javax.annotation.Nullable;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 20:23
 */
public abstract class ComSideSelector extends BaseComponent {
    // Variables
    protected boolean initialized = false;

    protected double scale;
    protected int diameter;
    protected boolean highlightSelected;

    protected IBlockState state;

    protected TrackballMC trackball;
    protected EnumFacing lastSideHovered;

    protected final SidePicker picker;

    /**
     * Creates the side selector object
     *
     * @param parent       The parent GUI
     * @param x            The x pos
     * @param y            The y pos
     * @param scaleValue   The scale
     * @param state        The block state of the block to render
     * @param doHighlights Render the highlights when selecting
     */
    public ComSideSelector(IGui parent, int x, int y, double scaleValue, @Nullable IBlockState state, boolean doHighlights) {
        super(parent, x, y);
        this.scale = scaleValue;
        this.state = state;
        this.highlightSelected = doHighlights;

        this.diameter = MathHelper.ceil(scale * Math.sqrt(3));
        trackball = new TrackballMC(1, 40);

        picker = new SidePicker(0.5);
    }

    /**
     * 0 : Normal Click (to the next mode)
     * 1 : Shift Click (to default or disabled)
     * 2 : Control Click (backward)
     */
    protected abstract void onSideToggled(EnumFacing side, int modifier);

    /**
     * As a general rule:
     * RED     : Disabled
     * BLUE    : Input
     * ORANGE  : Output
     * GREEN   : Both
     */
    @Nullable
    protected abstract Color getColorForMode(EnumFacing side);

    @Override
    public void mouseDown(int x, int y, int button) {
        lastSideHovered = null;
        super.mouseDown(x, y, button);
    }

    @Override
    public void mouseUp(int x, int y, int button) {
        if (button == 0 && lastSideHovered != null)
            onSideToggled(lastSideHovered, KeyHelper.isShiftPressed() ? 1 : (KeyHelper.isCtrlPressed() ? 2 : 0));
        super.mouseUp(x, y, button);
    }

    @Override
    public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (!initialized || Mouse.isButtonDown(2)) {
            trackball.setTransform(RenderUtils.createEntityRotateMatrix(ClientProxy.mc.getRenderViewEntity()));
            initialized = true;
        }

        int width = getWidth();
        int height = getHeight();

        GlStateManager.pushMatrix();
        GlStateManager.translate(xPos + width / 2, yPos + height / 2, (float) diameter);
        GlStateManager.scale(scale, -scale, scale);
        trackball.update(mouseX - width, -(mouseY - height));

        if (state != null) {
            RenderUtils.renderBlock(ClientProxy.mc.world, BlockPos.ORIGIN, state);
        }

        Color[] selections = new Color[highlightSelected ? 7 : 1];
        SidePicker.Hit hitCoord = picker.getNearestHit();
        if (hitCoord != null)
            selections[0] = getColorForMode(hitCoord.side);

        if (highlightSelected) {
            int i = 1;
            for (EnumFacing dir : EnumFacing.VALUES) {
                selections[i++] = getColorForMode(dir);
            }
        }

        lastSideHovered = hitCoord == null ? null : hitCoord.side;

        drawHighlights(lastSideHovered, selections);

        GlStateManager.popMatrix();
    }

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    @Override
    public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        // No Op
    }

    @Override
    public int getWidth() {
        return diameter;
    }

    @Override
    public int getHeight() {
        return diameter;
    }

    /**
     * Draws the highlights onto the block
     *
     * @param selections The colors to render
     */
    private void drawHighlights(EnumFacing face, Color[] selections) {
        EnumFacing[] values = EnumFacing.VALUES;
        if (selections.length == 1) {
            if (face == null)
                return;
            Color color = selections[0];
            if (color != null) {
                RenderUtils.setColor(color);
                if (color.getAlpha() == 0)
                    return;
            }
            prepareRenderState();
            drawQuad(face);
        } else {
            prepareRenderState();
            for (int i = 0; i < 7; i++) {
                Color color = selections[i];
                if (color != null) {
                    RenderUtils.setColor(color);
                    if (color.getAlpha() == 0)
                        continue;
                }
                EnumFacing dir = i == 0 ? face : values[i - 1];

                if (dir != null)
                    drawQuad(dir);
            }
        }
        restoreRenderState();
    }

    private static void prepareRenderState() {
        GlStateManager.disableLighting();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();


        GL11.glBegin(GL11.GL_QUADS);
    }

    private static void restoreRenderState() {
        GL11.glEnd();

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
    }

    private static void drawQuad(EnumFacing dir) {
        switch (dir) {
            case EAST:
                GL11.glVertex3f(0.5f, -0.5f, -0.5f);
                GL11.glVertex3f(0.5f, 0.5f, -0.5f);
                GL11.glVertex3f(0.5f, 0.5f, 0.5f);
                GL11.glVertex3f(0.5f, -0.5f, 0.5f);
                break;
            case UP:
                GL11.glVertex3f(-0.5f, 0.5f, -0.5f);
                GL11.glVertex3f(-0.5f, 0.5f, 0.5f);
                GL11.glVertex3f(0.5f, 0.5f, 0.5f);
                GL11.glVertex3f(0.5f, 0.5f, -0.5f);
                break;
            case SOUTH:
                GL11.glVertex3f(-0.5f, -0.5f, 0.5f);
                GL11.glVertex3f(0.5f, -0.5f, 0.5f);
                GL11.glVertex3f(0.5f, 0.5f, 0.5f);
                GL11.glVertex3f(-0.5f, 0.5f, 0.5f);
                break;
            case WEST:
                GL11.glVertex3f(-0.5f, -0.5f, -0.5f);
                GL11.glVertex3f(-0.5f, -0.5f, 0.5f);
                GL11.glVertex3f(-0.5f, 0.5f, 0.5f);
                GL11.glVertex3f(-0.5f, 0.5f, -0.5f);
                break;
            case DOWN:
                GL11.glVertex3f(-0.5f, -0.5f, -0.5f);
                GL11.glVertex3f(0.5f, -0.5f, -0.5f);
                GL11.glVertex3f(0.5f, -0.5f, 0.5f);
                GL11.glVertex3f(-0.5f, -0.5f, 0.5f);
                break;
            case NORTH:
                GL11.glVertex3f(-0.5f, -0.5f, -0.5f);
                GL11.glVertex3f(-0.5f, 0.5f, -0.5f);
                GL11.glVertex3f(0.5f, 0.5f, -0.5f);
                GL11.glVertex3f(0.5f, -0.5f, -0.5f);
                break;
            default:
        }
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
        this.diameter = MathHelper.ceil(scale * Math.sqrt(3));
    }

    public IBlockState getBlockState() {
        return state;
    }

    public void setBlockState(IBlockState blockState) {
        this.state = blockState;
    }

    public boolean isHighlightSelected() {
        return highlightSelected;
    }

    public void setHighlightSelected(boolean highlightSelected) {
        this.highlightSelected = highlightSelected;
    }
}
