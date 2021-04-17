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

import ilib.ImpLib;
import ilib.client.util.RenderUtils;
import ilib.gui.IGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public abstract class BaseComponent extends Gui {
    public static final ResourceLocation COMPONENTS_TEXTURE = new ResourceLocation(ImpLib.MODID, "textures/gui/components.png");
    // Variables
    protected int xPos, yPos;
    protected int imgX, imgY;
    protected IGui owner;
    protected ResourceLocation texture;

    protected static List<String> toolTip = new ArrayList<>();

    protected IGuiListener guiListener;

    /**
     * mi.Main constructor for all components
     *
     * @param parentGui The parent Gui
     * @param x         The x position
     * @param y         The y position
     */
    public BaseComponent(IGui parentGui, int x, int y) {
        setOwner(parentGui);
        xPos = x;
        yPos = y;
    }

    public void onInit() {
    }

    /**
     * Called to render the component
     */
    public abstract void render(int guiLeft, int guiTop, int mouseX, int mouseY);

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    public abstract void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY);

    /**
     * Used to find how wide this is
     *
     * @return How wide the component is
     */
    public abstract int getWidth();

    /**
     * Used to find how tall this is
     *
     * @return How tall the component is
     */
    public abstract int getHeight();

    /**
     * Used to determine if a dynamic tooltip is needed at runtime
     *
     * @param mouseX Mouse X Pos
     * @param mouseY Mouse Y Pos
     */
    public void getDynamicToolTip(List<String> toolTip, int mouseX, int mouseY) {
    }

    /**
     * Render the tooltip if you can
     *
     * @param mouseX Mouse X
     * @param mouseY Mouse Y
     */
    public void renderToolTip(int mouseX, int mouseY) {
        toolTip.clear();
        getDynamicToolTip(toolTip, mouseX - owner.getGuiLeft(), mouseY - owner.getGuiTop());
        if (!toolTip.isEmpty())
            drawHoveringText(toolTip, mouseX, mouseY, owner.getFontRenderer());
    }

    /**
     * Used to get what area is being displayed, mainly used for JEI
     */
    public Rectangle getArea(int guiLeft, int guiTop) {
        return new Rectangle(xPos + guiLeft, yPos + guiTop, getWidth(), getHeight());
    }

    /**
     * Called when the mouse is pressed
     *
     * @param x      Mouse X Position
     * @param y      Mouse Y Position
     * @param button Mouse Button
     */
    public void mouseDown(int x, int y, int button) {
        if (guiListener != null)
            guiListener.onMouseDown(this, x, y, button);
    }

    /**
     * 改进版drawTexturedModalRect
     * support 255+
     */
    @Override
    public final void drawTexturedModalRect(int x, int y, int u, int v, int w, int h) {
        //if(this.texture != null) {
        //    RenderUtils.bindTexture(this.texture);
        //}
        if (imgX == 256 && imgY == 256) {
            if (w > 256 || h > 256)
                throw new IllegalStateException("Please change texture size!");
            RenderUtils.fastRect(x, y, u, v, w, h);
        } else {
            RenderUtils.fastRect(x, y, u, v, w, h, imgX, imgY);
        }
        //if(this.texture != null) {
        //    RenderUtils.bindTexture(parent.getResLoc());
        //}
    }

    /**
     * Called when the mouse button is over the component and released
     *
     * @param x      Mouse X Position
     * @param y      Mouse Y Position
     * @param button Mouse Button
     */
    public void mouseUp(int x, int y, int button) {
        if (guiListener != null)
            guiListener.onMouseUp(this, x, y, button);
    }

    /**
     * Called when the user drags the component
     *
     * @param x      Mouse X Position
     * @param y      Mouse Y Position
     * @param button Mouse Button
     * @param time   How long
     */
    public void mouseDrag(int x, int y, int button, long time) {
        if (guiListener != null)
            guiListener.onMouseDrag(this, x, y, button, time);
    }

    /**
     * Called when the mouse is scrolled
     *
     * @param dir 1 for positive, -1 for negative
     */
    public void mouseScrolled(int x, int y, int dir) {
    }

    /**
     * Used to check if the mouse if currently over the component
     * <p>
     * You must have the getWidth() and getHeight() functions defined for this to work properly
     *
     * @param mouseX Mouse X Position
     * @param mouseY Mouse Y Position
     * @return True if mouse if over the component
     */
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= xPos && mouseX < xPos + getWidth() && mouseY >= yPos && mouseY < yPos + getHeight();
    }

    /**
     * Used when a key is pressed
     *
     * @param letter  The letter
     * @param keyCode The code
     */
    public void keyTyped(char letter, int keyCode) {
        if (guiListener != null)
            guiListener.keyTyped(this, letter, keyCode);
    }

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public final int getXPos() {
        return xPos;
    }

    public final void setXPos(int xPos) {
        this.xPos = xPos;
    }

    public final int getYPos() {
        return yPos;
    }

    public final void setYPos(int yPos) {
        this.yPos = yPos;
    }

    public final ResourceLocation getTexture() {
        return this.texture;
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseComponent> T setTexture(ResourceLocation loc) {
        this.texture = loc;
        return (T) this;
    }

    public final IGui getOwner() {
        return owner;
    }

    public final void setOwner(IGui owner) {
        if (owner == null) return;
        this.owner = owner;
        this.imgX = owner.getImgXSize();
        this.imgY = owner.getImgYSize();
        this.texture = owner.getTexture();
    }

    public final IGuiListener getGuiListener() {
        return guiListener;
    }

    public final void setGuiListener(IGuiListener l) {
        this.guiListener = l;
    }

    /*******************************************************************************************************************
     * Helper Methods                                                                                                  *
     *******************************************************************************************************************/

    /**
     * Used to draw a tooltip over this component
     *
     * @param tip    The list of strings to render
     * @param mouseX The mouse x Position
     * @param mouseY The mouse Y position
     * @param font   The font renderer
     */
    protected final void drawHoveringText(List<String> tip, int mouseX, int mouseY, FontRenderer font) {
        if (!tip.isEmpty()) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, 5);

            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            int maxWidth = 0;
            for (String s : tip) {
                int width = font.getStringWidth(s);
                if (width > maxWidth) {
                    maxWidth = width;
                }
            }
            int x = mouseX + 12;
            int y = mouseY - 12;
            int dY = 8;
            if (tip.size() > 1) {
                dY += 2 + (tip.size() - 1) * 10;
            }
            if (x + maxWidth > getOwner().getXSize()) {
                x -= 28 + maxWidth;
            }
            /*if (y + dY + 6 > parent.getYSize()) {
                y = this.getHeight() - dY - 6;
            }*/

            prepare();

            drawTipBG(x - 3, y - 4, x + maxWidth + 3, y - 3);
            drawTipBG(x - 3, y + dY + 3, x + maxWidth + 3, y + dY + 4);
            drawTipBG(x - 3, y - 3, x + maxWidth + 3, y + dY + 3);
            drawTipBG(x - 4, y - 3, x - 3, y + dY + 3);
            drawTipBG(x + maxWidth + 3, y - 3, x + maxWidth + 4, y + dY + 3);

            drawTipFG(x - 3, y - 3 + 1, x - 3 + 1, y + dY + 3 - 1);
            drawTipFG(x + maxWidth + 2, y - 3 + 1, x + maxWidth + 3, y + dY + 3 - 1);
            drawTipFG(x - 3, y - 3, x + maxWidth + 3, y - 3 + 1);
            drawTipFG(x - 3, y + dY + 2, x + maxWidth + 3, y + dY + 3);

            done();

            int i = 0;
            for (String s : tip) {
                font.drawStringWithShadow(s, x, y, -1);
                if (i++ == 0)
                    y += 2;
                y += 10;
            }

            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderHelper.enableStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);

            GL11.glPopMatrix();
        }
    }

    protected static void prepare() {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(7425);
    }

    protected static void drawTipBG(int a, int b, int c, int d) {
        final int z = 300;

        BufferBuilder buf = RenderUtils.BUILDER;
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(c, b, z).color(0, 16, 16, 240).endVertex();
        buf.pos(a, b, z).color(0, 16, 16, 240).endVertex();
        buf.pos(a, d, z).color(0, 16, 16, 240).endVertex();
        buf.pos(c, d, z).color(0, 16, 16, 240).endVertex();
        RenderUtils.TESSELLATOR.draw();
    }

    protected static void drawTipFG(int a, int b, int c, int d) {
        final int z = 300;

        BufferBuilder buf = RenderUtils.BUILDER;
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(c, b, z).color(0, 80, 80, 255).endVertex();
        buf.pos(a, b, z).color(0, 80, 80, 255).endVertex();
        buf.pos(a, d, z).color(0, 40, 80, 127).endVertex();
        buf.pos(c, d, z).color(0, 40, 80, 127).endVertex();
        RenderUtils.TESSELLATOR.draw();
    }

    protected static void done() {
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

}
