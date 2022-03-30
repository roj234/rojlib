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
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.awt.*;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GColoredQuad extends SimpleComponent {
    // Variables
    protected Color color;

    public GColoredQuad(IGui parent, int x, int y, int w, int h, Color color) {
        super(parent, x, y, w, h);
        this.color = color;
    }

    /*******************************************************************************************************************
     * Component                                                                                                       *
     *******************************************************************************************************************/

    @Nullable
    protected Color getDynamicColor() {
        return color;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        color = getDynamicColor();
        if (color == null || color.getAlpha() == 0) return;

        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();

        RenderUtils.setColor(color);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(xPos, yPos, 10);
        GL11.glVertex3d(xPos, yPos + height, 10);
        GL11.glVertex3d(xPos + width, yPos + height, 10);
        GL11.glVertex3d(xPos + width, yPos, 10);
        GL11.glEnd();

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
    }

    @Override
    public void renderOverlay(int mouseX, int mouseY) {}

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
}
