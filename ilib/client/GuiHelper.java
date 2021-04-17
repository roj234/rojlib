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
package ilib.client;

import ilib.ClientProxy;
import ilib.ImpLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.SoundEvents;
import roj.util.Color;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 23:56
 */
public class GuiHelper {
    public static void openClientGui(GuiScreen screen) {
        if (!ImpLib.proxy.isMainThread(true)) {
            ImpLib.proxy.runAtMainThread(true, () -> openClientGui(screen));
            return;
        }
        Minecraft mc = ClientProxy.mc;
        mc.displayGuiScreen(screen);
        mc.setIngameNotInFocus();
        mc.mouseHelper.ungrabMouseCursor();
    }

    public static void closeClientGui() {
        openClientGui(null);
    }

    public static void playButtonSound() {
        ClientProxy.mc.getSoundHandler()
                .playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static boolean isInBounds(int x, int y, int a, int b, int c, int d) {
        return x >= a && x <= c && y >= b && y <= d;
    }

    public static boolean isInRect(int x, int y, int xSize, int ySize,
                                   int mouseX, int mouseY) {
        return (((mouseX >= x) && (mouseX <= (x + xSize))) &&
                ((mouseY >= y) && (mouseY <= (y + ySize))));
    }

    public static int getGuiScale() {
        Minecraft mc = ClientProxy.mc;
        int scale = 1;

        int k = mc.gameSettings.guiScale;
        if (k == 0) {
            k = 1000;
        }

        while (scale < k && mc.displayWidth / (scale + 1) >= 320 && mc.displayHeight / (scale + 1) >= 240) {
            ++scale;
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
}
