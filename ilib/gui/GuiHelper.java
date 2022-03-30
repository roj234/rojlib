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
package ilib.gui;

import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.anim.Animation;
import ilib.client.RenderUtils;
import ilib.gui.comp.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;

import java.awt.*;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/30 23:56
 */
public class GuiHelper {
    public static void openClientGui(GuiScreen screen) {
        if (!ImpLib.proxy.isMainThread(true)) {
            ImpLib.proxy.runAtMainThread(true, () -> openClientGui(screen));
            return;
        }
        ClientProxy.mc.displayGuiScreen(screen);
    }

    public static void playButtonSound() {
        ClientProxy.mc.getSoundHandler()
                .playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static boolean isInBounds(int x, int y, int a, int b, int c, int d) {
        return x >= a && x <= c && y >= b && y <= d;
    }

    @Deprecated
    public static boolean isInRect(int x, int y, int xSize, int ySize,
                                   int mouseX, int mouseY) {
        return (((mouseX >= x) && (mouseX <= (x + xSize))) &&
                ((mouseY >= y) && (mouseY <= (y + ySize))));
    }

    public static int getGuiScale() {
        Minecraft mc = ClientProxy.mc;

        int scale = 1;
        boolean unicodeFlag = mc.isUnicode();
        int k = mc.gameSettings.guiScale;
        if (k == 0) {
            k = 1000;
        }

        while (scale < k && mc.displayWidth / (scale + 1) >= 320 && mc.displayHeight / (scale + 1) >= 240) {
            ++scale;
        }

        if (unicodeFlag && scale % 2 != 0 && scale != 1) {
            --scale;
        }

        return scale;
    }

    /**
     * 混合颜色
     *
     * @param blending x的占比 0 - 1
     */
    public static Color mixColorWithBlend(Color x, Color y, float blending) {
        float inverseBlending = 1 - blending;

        // Interpolate Values
        int red = (int) (y.getRed() * blending + x.getRed() * inverseBlending);
        int green = (int) (y.getGreen() * blending + x.getGreen() * inverseBlending);
        int blue = (int) (y.getBlue() * blending + x.getBlue() * inverseBlending);
        int alpha = (int) (y.getAlpha() * blending + x.getAlpha() * inverseBlending);

        return new Color(red, green, blue, alpha);
    }

    // region 渲染部分

    public static void renderBackground(int rx, int ry, List<Component> components) {
        for (int i = 0; i < components.size(); i++) {
            Component com = components.get(i);
            Animation anim = com.getAnimation();
            if (anim != null) {
                GlStateManager.pushMatrix();
                anim.apply();
            }
            RenderUtils.prepareRenderState();
            com.render(rx, ry);
            if (anim != null) {
                GlStateManager.popMatrix();
                if (!anim.isPlaying()) com.setAnimation(null);
            }
        }
        RenderUtils.restoreRenderState();
    }

    public static void renderForeground(int rx, int ry, List<Component> components) {
        for (int i = 0; i < components.size(); i++) {
            Component com = components.get(i);
            Animation anim = com.getAnimation();
            if (anim != null) {
                GlStateManager.pushMatrix();
                anim.apply();
            }
            RenderUtils.prepareRenderState();
            com.renderOverlay(rx, ry);
            if (anim != null) {
                GlStateManager.popMatrix();
                if (!anim.isPlaying()) com.setAnimation(null);
            }
        }
        RenderUtils.restoreRenderState();
    }

    // endregion
}
