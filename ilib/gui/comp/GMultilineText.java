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

import ilib.gui.IGui;
import ilib.gui.util.Align;
import ilib.util.MCTexts;
import roj.text.SimpleLineReader;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;

import static ilib.ClientProxy.mc;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GMultilineText extends Component {
    protected static final int COLOR_DEFAULT = 4210752;

    protected List<String> text;
    protected Color color;
    protected Align align = Align.LEFT;

    public GMultilineText(IGui parent, int x, int y, String text, @Nullable Color color) {
        super(parent, x, y);
        this.text = SimpleLineReader.slrParserV2(text, false);
        this.color = color;
    }

    public GMultilineText(IGui parent, int x, int y, List<String> text, @Nullable Color color) {
        super(parent, x, y);
        this.text = text;
        this.color = color;
    }

    public static GMultilineText alignCenterY(IGui parent, int width, int y, String text, @Nullable Color color) {
        GMultilineText c = new GMultilineText(parent, 0, y, text, color);
        c.xPos = (width - c.getWidth()) / 2;
        return c;
    }

    /*******************************************************************************************************************
     * Overrides                                                                                                       *
     *******************************************************************************************************************/

    @Override
    public void render(int mouseX, int mouseY) {
        int w = getWidth();
        int h = getHeight();

        int x = xPos;
        int y = yPos;

        switch (align) {
            case RIGHT:
                x -= w;
                break;
            case CENTER:
                x -= w / 2;
                break;
            case LEFT:
                break;
        }

        int rgb = color == null ? COLOR_DEFAULT : color.getRGB();
        for (int i = 0; i < text.size(); i++) {
            int diff;
            switch (align) {
                case RIGHT:
                    diff = w - MCTexts.getStringWidth(text.get(i));
                    break;
                case CENTER:
                    diff = (w - MCTexts.getStringWidth(text.get(i))) / 2;
                    break;
                default:
                case LEFT:
                    diff = 0;
                    break;
            }
            mc.fontRenderer.drawStringWithShadow(text.get(i), x + diff, y, rgb);
            y += 10;
        }
    }

    @Override
    public void render2(int mouseX, int mouseY) {}

    /*******************************************************************************************************************
     * Accessors/Mutators                                                                                              *
     *******************************************************************************************************************/

    @Override
    public int getWidth() {
        int w = 0;
        for (int i = 0; i < text.size(); i++) {
            int w1 = MCTexts.getStringWidth(text.get(i));
            if (w1 > w) w = w1;
        }
        return w;
    }

    @Override
    public int getHeight() {
        return 10 * text.size() - 3;
    }

    public List<String> getText() {
        return text;
    }

    public void setText(List<String> text) {
        this.text = text;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
}
