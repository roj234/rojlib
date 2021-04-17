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

import ilib.gui.IGui;
import ilib.gui.comp.BaseComponent;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import roj.text.TextUtil;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public abstract class ComNumberInput extends BaseComponent {
    public static final int ERROR_COLOR = 0xE62E00;
    public static final int NORMAL_COLOR = 0xFFFFFF;

    // Variables
    protected int width, height, value, min, max;
    protected GuiTextField textField;

    public ComNumberInput(IGui parent, int x, int y, int width, int height, int value, int lowestValue, int highestValue) {
        super(parent, x, y);
        this.width = width;
        this.height = height;
        this.value = value;
        this.min = lowestValue;
        this.max = highestValue;
    }

    @Override
    public void onInit() {
        textField = new GuiTextField(0, owner.getFontRenderer(), xPos, yPos, width, height);
        textField.setText(String.valueOf(value));
        textField.setTextColor(NORMAL_COLOR);
        textField.setMaxStringLength(10);
    }

    /**
     * Called when the user sets the value or when the value is changed
     *
     * @param value The value set by the user
     */
    protected abstract void setValue(int value);

    /**
     * Called when the mouse is pressed
     *
     * @param x      Mouse X Position
     * @param y      Mouse Y Position
     * @param button Mouse Button
     */
    @Override
    public void mouseDown(int x, int y, int button) {
        textField.mouseClicked(x, y, button);
        super.mouseDown(x, y, button);
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return true;
    }

    private boolean lastColor = false;

    /**
     * Used when a key is pressed
     *
     * @param letter  The letter
     * @param keyCode The code
     */
    @Override
    public void keyTyped(char letter, int keyCode) {
        super.keyTyped(letter, keyCode);
        if (Character.isLetter(letter) || keyCode == 1) return;

        if (!textField.isFocused()) return;

        if (keyCode == 28 && !lastColor) { // 回车
            textField.setFocused(false);
        }

        textField.textboxKeyTyped(letter, keyCode);
        if (TextUtil.isNumber(textField.getText()) != 0) {
            if (!lastColor) {
                textField.setTextColor(ERROR_COLOR);
                lastColor = true;
            }
            return;
        }

        int value;
        try {
            value = Integer.parseInt(textField.getText());
        } catch (NumberFormatException e) {
            if (!lastColor) {
                textField.setTextColor(ERROR_COLOR);
                lastColor = true;
            }
            return;
        }
        if (lastColor) {
            textField.setTextColor(NORMAL_COLOR);
            lastColor = false;
        }

        if (value > max) {
            textField.setText(String.valueOf(max));
            value = max;
        } else if (value < min) {
            textField.setText(String.valueOf(min));
            value = min;
        }

        if (this.value != value) {
            setValue(this.value = value);
        }
    }

    /**
     * Called to render the component
     */
    @Override
    public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        textField.drawTextBox();
        GlStateManager.popMatrix();
    }

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    @Override
    public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        //GlStateManager.pushMatrix();
        //textField.drawTextBox();
        //GlStateManager.popMatrix();
        // No Op
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

    public int getValue() {
        return value;
    }

    public int min() {
        return min;
    }

    public void min(int min) {
        this.min = min;
    }

    public int max() {
        return max;
    }

    public void max(int max) {
        this.max = max;
    }

    public GuiTextField getTextField() {
        return textField;
    }

    public void setTextField(GuiTextField textField) {
        this.textField = textField;
    }
}
