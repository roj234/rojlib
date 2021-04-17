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
import ilib.util.TextHelperM;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public abstract class ComTextInput extends BaseComponent {
    // Variables
    protected int width, height;
    protected GuiTextField textField;
    private String label;

    /**
     * Creates the text box
     *
     * @param label The default label, will translate, can be null
     */
    public ComTextInput(IGui parent, int x, int y, int boxWidth, int boxHeight, @Nullable String label) {
        super(parent, x, y);
        this.width = boxWidth;
        this.height = boxHeight;
        this.label = label;
    }

    @Override
    public void onInit() {
        textField = new GuiTextField(0, owner.getFontRenderer(), xPos, yPos, width, height);
        if (label != null)
            textField.setText(TextHelperM.translate(label));

        textField.setTextColor(0xFFFFFF);
        textField.setMaxStringLength(width);
    }

    /**
     * Called when the value in the text box changes
     *
     * @param value The current value
     */
    protected abstract void fieldUpdated(String value);

    @Override
    public void mouseDown(int x, int y, int button) {
        textField.mouseClicked(x, y, button);
        if (button == 1 && textField.isFocused()) {
            fieldUpdated(textField.getText());
        }
        super.mouseDown(x, y, button);
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return true;
    }

    private boolean lastColor = false;

    @Override
    public void keyTyped(char letter, int keyCode) {
        super.keyTyped(letter, keyCode);
        if (keyCode == 1 || !textField.isFocused()) return;

        if (keyCode == 28 && !lastColor) { // 回车
            textField.setFocused(false);
        } else {
            textField.textboxKeyTyped(letter, keyCode);
            if (textField.getText() == null || textField.getText().length() < 1) {
                if (!lastColor) {
                    textField.setTextColor(0xE62E00);
                    lastColor = true;
                }
                return;
            }
            if (lastColor) {
                textField.setTextColor(0xFFFFFF);
                lastColor = false;
            }
        }

        if (!textField.getText().equals(label)) {
            fieldUpdated(label = textField.getText());
        }
    }

    /**
     * Called to render the component
     */
    @Override
    public void render(int guiLeft, int guiTop, int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        textField.drawTextBox();
        GlStateManager.disableAlpha();
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GlStateManager.popMatrix();
    }

    /**
     * Called after base render, is already translated to guiLeft and guiTop, just move offset
     */
    @Override
    public void renderOverlay(int guiLeft, int guiTop, int mouseX, int mouseY) {
        // NO OP
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    public GuiTextField getTextField() {
        return textField;
    }

    public void setTextField(GuiTextField textField) {
        this.textField = textField;
    }
}
