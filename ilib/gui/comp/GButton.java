package ilib.gui.comp;

import ilib.ClientProxy;
import ilib.client.RenderUtils;
import ilib.gui.GuiHelper;
import ilib.gui.IGui;
import ilib.gui.util.ComponentListener;
import ilib.gui.util.Sprite;
import ilib.util.MCTexts;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.awt.*;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GButton extends SimpleComponent {
	public static final int BUTTON_CLICKING = 1, BUTTON_HOVERED = 2, BUTTON_TOGGLED = 4, CHANGE_V_BY_CLICKING = 8, CHANGE_V_BY_HOVER = 16, CHANGE_V_BY_TOGGLE = 32, BUTTON_ENABLED = 64, HANDLE_TOGGLE = 128, BUTTON_MUTED = 256;

	protected int u, v;
	protected Object label;
	protected Color color = GText.COLOR_DEFAULT;

	protected int flag;

	/**
	 * Construct a button without texture
	 */
	public GButton(IGui parent, int x, int y, int w, int h, @Nullable Object text) {
		super(parent, x, y, w, h);
		u = v = -1;
		setLabel(text);
		setHoverButton();
	}

	public GButton(IGui parent, int x, int y, int u, int v, int w, int h) {
		this(parent, x, y, u, v, w, h, null);
	}

	public GButton(IGui parent, int x, int y, int u, int v, int w, int h, @Nullable Object label) {
		super(parent, x, y, w, h);
		this.u = u;
		this.v = v;
		setHoverButton();
		setLabel(label);
	}

	public GButton(IGui parent, int x, int y, Sprite sprite) {
		this(parent, x, y, sprite, null);
	}

	public GButton(IGui parent, int x, int y, Sprite sprite, @Nullable Object label) {
		super(parent, x, y, sprite.w(), sprite.h());
		u = sprite.u();
		v = sprite.v();
		setTexture(sprite.texture());
		setHoverButton();
		setLabel(label);
	}

	public GButton setCheckbox() {
		flag = CHANGE_V_BY_TOGGLE | BUTTON_ENABLED | HANDLE_TOGGLE;
		return this;
	}

	public GButton setClickButton() {
		flag = CHANGE_V_BY_CLICKING | BUTTON_ENABLED;
		return this;
	}

	public GButton setHoverButton() {
		flag = CHANGE_V_BY_HOVER | BUTTON_ENABLED;
		return this;
	}

	public GButton setDummy() {
		flag = BUTTON_ENABLED | CHANGE_V_BY_TOGGLE | BUTTON_MUTED;
		return this;
	}

	/*******************************************************************************************************************
	 * Overrides                                                                                                       *
	 *******************************************************************************************************************/

	protected void doAction() {}

	@Override
	public void mouseDown(int mouseX, int mouseY, int button) {
		super.mouseDown(mouseX, mouseY, button);

		if ((flag & BUTTON_ENABLED) == 0) return;

		if ((flag & BUTTON_MUTED) == 0) GuiHelper.playButtonSound();

		if ((flag & HANDLE_TOGGLE) != 0) flag ^= BUTTON_TOGGLED;

		if (listener != null) listener.actionPerformed(this, ComponentListener.BUTTON_CLICKED);
		doAction();

		flag |= BUTTON_CLICKING;
	}

	@Override
	public void mouseUp(int x, int y, int button) {
		super.mouseUp(x, y, button);
		flag &= ~BUTTON_CLICKING;
	}

	@Override
	public boolean isMouseOver(int mouseX, int mouseY) {
		boolean b = checkMouseOver(mouseX, mouseY);
		if (b) {flag |= BUTTON_HOVERED;} else flag &= ~(BUTTON_CLICKING | BUTTON_HOVERED);
		return b;
	}

	@Override
	public void render(int mouseX, int mouseY) {
		int v = getV();
		if (v >= 0) {
			RenderUtils.bindTexture(getTexture());
			if ((flag & (CHANGE_V_BY_CLICKING | BUTTON_CLICKING)) == (CHANGE_V_BY_CLICKING | BUTTON_CLICKING)) {
				v += height;
			} else if ((flag & (CHANGE_V_BY_HOVER | BUTTON_HOVERED)) == (CHANGE_V_BY_HOVER | BUTTON_HOVERED)) {
				v += height;
			} else if ((flag & (CHANGE_V_BY_TOGGLE | BUTTON_TOGGLED)) == (CHANGE_V_BY_TOGGLE | BUTTON_TOGGLED)) {
				v += height;
			}
			RenderUtils.fastRect(xPos, yPos, getU(), v, width, height);
		}
	}

	@Override
	public void render2(int mouseX, int mouseY) {
		if (label instanceof String) {
			FontRenderer fr = ClientProxy.mc.fontRenderer;

			String label = this.label.toString();

			int size = MCTexts.getStringWidth(label);

			fr.drawString(label, xPos + (width - size) / 2f, yPos + (height - fr.FONT_HEIGHT) / 2f, color.getRGB(), false);
		} else if (label instanceof ItemStack) {
			RenderHelper.enableGUIStandardItemLighting();

			ClientProxy.mc.getRenderItem().renderItemAndEffectIntoGUI((ItemStack) label, xPos + (width / 2) - 8, yPos + (height / 2) - 8);

			RenderHelper.disableStandardItemLighting();
		}
	}

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	public final boolean isClicking() {
		return (flag & BUTTON_CLICKING) != 0;
	}

	public final boolean isHovered() {
		return (flag & BUTTON_HOVERED) != 0;
	}

	public final boolean isToggled() {
		return (flag & BUTTON_TOGGLED) != 0;
	}

	public final GButton setToggled(boolean toggled) {
		if (toggled) {flag |= BUTTON_TOGGLED;} else flag &= ~BUTTON_TOGGLED;
		return this;
	}

	public final boolean isEnabled() {
		return (flag & BUTTON_ENABLED) != 0;
	}

	public final GButton setEnabled(boolean enabled) {
		if (enabled) {flag |= BUTTON_ENABLED;} else flag &= ~BUTTON_ENABLED;
		return this;
	}

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

	public String getLabel() {
		return String.valueOf(label);
	}

	public Object getLabelRaw() {
		return label;
	}

	public void setLabel(Object label) {
		this.label = label instanceof String ? MCTexts.format(label.toString()) : label;
		if (width == 0) {
			if (label instanceof String) {
				width = MCTexts.getStringWidth(label.toString()) + 20;
				if (width < 32) width = 20;
			} else {
				width = height = 22;
			}
		}
	}

	public Color getColor() {
		return color;
	}

	public GButton setColor(Color color) {
		this.color = color;
		return this;
	}

	public int getFlag() {
		return flag;
	}

	public GButton setFlag(int flag) {
		this.flag = flag;
		return this;
	}
}
