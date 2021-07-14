package lac.client.login;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.GlStateManager.LogicOp;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.math.MathHelper;
import roj.text.TextUtil;

public class PasswordField extends Gui {
    public final FontRenderer fr;
    public int x, y, w, h;
    public String text = "";
    public int maxLen = 32;
    public boolean bg = true, enabled = true;
    public int cursorPosition;
    public int selectionEnd;
    public int enabledColor = 14737632;
    public int disabledColor = 7368816;

    public PasswordField(int id, FontRenderer fr, int x, int y, int w, int h) {
        this.fr = fr;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void setText(String textIn) {
        if (textIn.length() > this.maxLen) {
            this.text = textIn.substring(0, this.maxLen);
        } else {
            this.text = textIn;
        }

        this.setCursorPositionEnd();
    }

    public String getSelectedText() {
        int _lvt_1_ = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
        int _lvt_2_ = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
        return this.text.substring(_lvt_1_, _lvt_2_);
    }

    public void writeText(String textToWrite) {
        String _lvt_2_ = "";
        String _lvt_3_ = ChatAllowedCharacters.filterAllowedCharacters(textToWrite);
        int _lvt_4_ = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
        int _lvt_5_ = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
        int _lvt_6_ = this.maxLen - this.text.length() - (_lvt_4_ - _lvt_5_);
        if (!this.text.isEmpty()) {
            _lvt_2_ = _lvt_2_ + this.text.substring(0, _lvt_4_);
        }

        int _lvt_8_;
        if (_lvt_6_ < _lvt_3_.length()) {
            _lvt_2_ = _lvt_2_ + _lvt_3_.substring(0, _lvt_6_);
            _lvt_8_ = _lvt_6_;
        } else {
            _lvt_2_ = _lvt_2_ + _lvt_3_;
            _lvt_8_ = _lvt_3_.length();
        }

        if (!this.text.isEmpty() && _lvt_5_ < this.text.length()) {
            _lvt_2_ = _lvt_2_ + this.text.substring(_lvt_5_);
        }

        this.text = _lvt_2_;
        this.moveCursorBy(_lvt_4_ - this.selectionEnd + _lvt_8_);
    }

    public void deleteWords(int num) {
        if (!this.text.isEmpty()) {
            if (this.selectionEnd != this.cursorPosition) {
                this.writeText("");
            } else {
                this.deleteFromCursor(this.getNthWordFromCursor(num) - this.cursorPosition);
            }
        }
    }

    public void deleteFromCursor(int num) {
        if (!this.text.isEmpty()) {
            if (this.selectionEnd != this.cursorPosition) {
                this.writeText("");
            } else {
                boolean _lvt_2_ = num < 0;
                int _lvt_3_ = _lvt_2_ ? this.cursorPosition + num : this.cursorPosition;
                int _lvt_4_ = _lvt_2_ ? this.cursorPosition : this.cursorPosition + num;
                String _lvt_5_ = "";
                if (_lvt_3_ >= 0) {
                    _lvt_5_ = this.text.substring(0, _lvt_3_);
                }

                if (_lvt_4_ < this.text.length()) {
                    _lvt_5_ = _lvt_5_ + this.text.substring(_lvt_4_);
                }

                this.text = _lvt_5_;
                if (_lvt_2_) {
                    this.moveCursorBy(num);
                }
            }
        }
    }

    public int getNthWordFromCursor(int numWords) {
        return this.getNthWordFromPos(numWords, this.cursorPosition);
    }

    public int getNthWordFromPos(int n, int _lvt_2_) {
        return this.getNthWordFromPosWS(n, _lvt_2_, true);
    }

    public int getNthWordFromPosWS(int n, int _lvt_2_, boolean _lvt_3_) {
        int _lvt_4_ = _lvt_2_;
        boolean _lvt_5_ = n < 0;
        int _lvt_6_ = Math.abs(n);

        for(int _lvt_7_ = 0; _lvt_7_ < _lvt_6_; ++_lvt_7_) {
            if (!_lvt_5_) {
                int _lvt_8_ = this.text.length();
                _lvt_4_ = this.text.indexOf(32, _lvt_4_);
                if (_lvt_4_ == -1) {
                    _lvt_4_ = _lvt_8_;
                } else {
                    while(_lvt_3_ && _lvt_4_ < _lvt_8_ && this.text.charAt(_lvt_4_) == ' ') {
                        ++_lvt_4_;
                    }
                }
            } else {
                while(_lvt_3_ && _lvt_4_ > 0 && this.text.charAt(_lvt_4_ - 1) == ' ') {
                    --_lvt_4_;
                }

                while(_lvt_4_ > 0 && this.text.charAt(_lvt_4_ - 1) != ' ') {
                    --_lvt_4_;
                }
            }
        }

        return _lvt_4_;
    }

    public void moveCursorBy(int num) {
        this.setCursorPosition(this.selectionEnd + num);
    }

    public void setCursorPosition(int pos) {
        this.cursorPosition = pos;
        int _lvt_2_ = this.text.length();
        this.cursorPosition = MathHelper.clamp(this.cursorPosition, 0, _lvt_2_);
        this.setSelectionPos(this.cursorPosition);
    }

    public void setCursorPositionZero() {
        this.setCursorPosition(0);
    }

    public void setCursorPositionEnd() {
        this.setCursorPosition(this.text.length());
    }

    public boolean textboxKeyTyped(char _lvt_1_, int keyCode) {
        if (GuiScreen.isKeyComboCtrlA(keyCode)) {
            this.setCursorPositionEnd();
            this.setSelectionPos(0);
            return true;
        } else if (GuiScreen.isKeyComboCtrlC(keyCode)) {
            GuiScreen.setClipboardString(this.getSelectedText());
            return true;
        } else if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            if (this.enabled) {
                this.writeText(GuiScreen.getClipboardString());
            }

            return true;
        } else if (GuiScreen.isKeyComboCtrlX(keyCode)) {
            GuiScreen.setClipboardString(this.getSelectedText());
            if (this.enabled) {
                this.writeText("");
            }

            return true;
        } else {
            switch(keyCode) {
                case 14:
                    if (GuiScreen.isCtrlKeyDown()) {
                        if (this.enabled) {
                            this.deleteWords(-1);
                        }
                    } else if (this.enabled) {
                        this.deleteFromCursor(-1);
                    }

                    return true;
                case 199:
                    if (GuiScreen.isShiftKeyDown()) {
                        this.setSelectionPos(0);
                    } else {
                        this.setCursorPositionZero();
                    }

                    return true;
                case 203:
                    if (GuiScreen.isShiftKeyDown()) {
                        if (GuiScreen.isCtrlKeyDown()) {
                            this.setSelectionPos(this.getNthWordFromPos(-1, this.getSelectionEnd()));
                        } else {
                            this.setSelectionPos(this.getSelectionEnd() - 1);
                        }
                    } else if (GuiScreen.isCtrlKeyDown()) {
                        this.setCursorPosition(this.getNthWordFromCursor(-1));
                    } else {
                        this.moveCursorBy(-1);
                    }

                    return true;
                case 205:
                    if (GuiScreen.isShiftKeyDown()) {
                        if (GuiScreen.isCtrlKeyDown()) {
                            this.setSelectionPos(this.getNthWordFromPos(1, this.getSelectionEnd()));
                        } else {
                            this.setSelectionPos(this.getSelectionEnd() + 1);
                        }
                    } else if (GuiScreen.isCtrlKeyDown()) {
                        this.setCursorPosition(this.getNthWordFromCursor(1));
                    } else {
                        this.moveCursorBy(1);
                    }

                    return true;
                case 207:
                    if (GuiScreen.isShiftKeyDown()) {
                        this.setSelectionPos(this.text.length());
                    } else {
                        this.setCursorPositionEnd();
                    }

                    return true;
                case 211:
                    if (GuiScreen.isCtrlKeyDown()) {
                        if (this.enabled) {
                            this.deleteWords(1);
                        }
                    } else if (this.enabled) {
                        this.deleteFromCursor(1);
                    }

                    return true;
                default:
                    if (ChatAllowedCharacters.isAllowedCharacter(_lvt_1_)) {
                        if (this.enabled) {
                            this.writeText(Character.toString(_lvt_1_));
                        }

                        return true;
                    } else {
                        return false;
                    }
            }
        }
    }

    public boolean mouseClicked(int x, int y, int mouseButton) {
        boolean inRegion = x >= this.x && x < this.x + this.w && y >= this.y && y < this.y + this.h;
        if (inRegion && mouseButton == 0) {
            int Dx = x - this.x;
            if (this.bg) {
                Dx -= 4;
            }

            String str = this.fr.trimStringToWidth(this.text, this.getWidth());
            this.setCursorPosition(this.fr.trimStringToWidth(str, Dx).length());
            return true;
        } else {
            return false;
        }
    }

    public void drawTextBox() {
        if (this.getEnableBackgroundDrawing()) {
            drawRect(this.x - 1, this.y - 1, this.x + this.w + 1, this.y + this.h + 1, -6250336);
            drawRect(this.x, this.y, this.x + this.w, this.y + this.h, -16777216);
        }

        int color = this.enabled ? this.enabledColor : this.disabledColor;
        int pos = this.cursorPosition;
        int selEnd = this.selectionEnd;
        String display = this.fr.trimStringToWidth(this.text, this.getWidth());
        boolean cursorIn = pos >= 0 && pos <= display.length();
        boolean showCursor = (System.currentTimeMillis() / 333 & 1) == 0 && cursorIn;
        int ofx = this.bg ? this.x + 4 : this.x;
        int ofy = this.bg ? this.y + (this.h - 8) / 2 : this.y;
        int ofx1 = ofx;
        if (selEnd > display.length()) {
            selEnd = display.length();
        }

        if (!display.isEmpty()) {
            String hl = TextUtil.repeat(cursorIn ? pos : display.length(), '*').toString();//cursorIn ? display.substring(0, pos) : display;
            ofx1 = this.fr.drawStringWithShadow(hl, (float)ofx, (float)ofy, color);
        }

        boolean bool1 = this.cursorPosition < this.text.length() || this.text.length() >= maxLen;
        int ofx2;
        if (!cursorIn) {
            ofx2 = pos > 0 ? ofx + this.w : ofx;
        } else if (bool1) {
            ofx2 = --ofx1;
        } else {
            ofx2 = ofx1;
        }

        if (!display.isEmpty() && cursorIn && pos < display.length()) {
            fr.drawStringWithShadow(TextUtil.repeat(pos, '*').toString()/*display.substring(pos)*/, ofx1, ofy, color);
        }

        if (showCursor) {
            if (bool1) {
                Gui.drawRect(ofx2, ofy - 1, ofx2 + 1, ofy + 1 + fr.FONT_HEIGHT, -3092272);
            } else {
                fr.drawStringWithShadow("_", ofx2, ofy, color);
            }
        }

        if (selEnd != pos) {
            int endMax = ofx + this.fr.getStringWidth("*") * selEnd;//display.substring(0, selEnd));
            this.drawSelectionBox(ofx2, ofy - 1, endMax - 1, ofy + 1 + fr.FONT_HEIGHT);
        }

    }

    private void drawSelectionBox(int sX, int sY, int eX, int eZ) {
        int tmp;
        if (sX < eX) {
            tmp = sX;
            sX = eX;
            eX = tmp;
        }

        if (sY < eZ) {
            tmp = sY;
            sY = eZ;
            eZ = tmp;
        }

        if (eX > this.x + this.w) {
            eX = this.x + this.w;
        }

        if (sX > this.x + this.w) {
            sX = this.x + this.w;
        }

        Tessellator ts = Tessellator.getInstance();
        BufferBuilder bb = ts.getBuffer();
        GlStateManager.color(0.0F, 0.0F, 255.0F, 255.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.enableColorLogic();
        GlStateManager.colorLogicOp(LogicOp.OR_REVERSE);
        bb.begin(7, DefaultVertexFormats.POSITION);
        bb.pos(sX, eZ, 0.0D).endVertex();
        bb.pos(eX, eZ, 0.0D).endVertex();
        bb.pos(eX, sY, 0.0D).endVertex();
        bb.pos(sX, sY, 0.0D).endVertex();
        ts.draw();
        GlStateManager.disableColorLogic();
        GlStateManager.enableTexture2D();
    }

    public void setMaxLen(int length) {
        this.maxLen = length;
        if (this.text.length() > length) {
            this.text = this.text.substring(0, length);
        }
    }

    public boolean getEnableBackgroundDrawing() {
        return this.bg;
    }

    public void setEnableBackgroundDrawing(boolean enableBackgroundDrawingIn) {
        this.bg = enableBackgroundDrawingIn;
    }

    public void setTextColor(int color) {
        this.enabledColor = color;
    }

    public void setDisabledTextColour(int color) {
        this.disabledColor = color;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSelectionEnd() {
        return this.selectionEnd;
    }

    public int getWidth() {
        return this.getEnableBackgroundDrawing() ? this.w - 8 : this.w;
    }

    public void setSelectionPos(int position) {
        int textlen = this.text.length();
        if (position > textlen) {
            position = textlen;
        }

        if (position < 0) {
            position = 0;
        }

        this.selectionEnd = position;
    }
}
