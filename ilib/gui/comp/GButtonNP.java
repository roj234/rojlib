package ilib.gui.comp;

import ilib.gui.DefaultSprites;
import ilib.gui.IGui;
import ilib.gui.util.NinePatchRenderer;

/**
 * @author solo6975
 * @since 2022/4/2 0:34
 */
public class GButtonNP extends GButton {
	protected static int UV_NORMAL = 0, UV_HIGHLIGHT = 1, UV_CLICK = 2, UV_DISABLE = 3;

	protected NinePatchRenderer buttonModel = DefaultSprites.BUTTON_A;

	public GButtonNP(IGui parent, int x, int y, Object label) {
		super(parent, x, y, 0, 20, label);
	}

	public GButtonNP(IGui parent, int x, int y, int w, int h, Object label) {
		super(parent, x, y, w, h, label);
	}

	public GButtonNP(IGui parent, int x, int y, int w, int h) {
		super(parent, x, y, w, h, null);
	}

	@Override
	public void render(int mouseX, int mouseY) {
		if ((flag & BUTTON_ENABLED) == 0) {
			setButtonUV(UV_DISABLE);
		} else if ((flag & (CHANGE_V_BY_CLICKING | BUTTON_CLICKING)) == (CHANGE_V_BY_CLICKING | BUTTON_CLICKING)) {
			setButtonUV(UV_CLICK);
		} else if ((flag & (CHANGE_V_BY_HOVER | BUTTON_HOVERED)) == (CHANGE_V_BY_HOVER | BUTTON_HOVERED) || (flag & (CHANGE_V_BY_TOGGLE | BUTTON_TOGGLED)) == (CHANGE_V_BY_TOGGLE | BUTTON_TOGGLED)) {
			setButtonUV(UV_HIGHLIGHT);
		} else {
			setButtonUV(UV_NORMAL);
		}

		buttonModel.render(xPos, yPos, u, v, width, height);
	}

	protected void setButtonUV(int state) {
		u = 0;
		v = state * buttonModel.getTextureSize();
	}

	public NinePatchRenderer getButtonModel() {
		return buttonModel;
	}

	public void setButtonModel(NinePatchRenderer buttonModel) {
		this.buttonModel = buttonModel;
	}
}
