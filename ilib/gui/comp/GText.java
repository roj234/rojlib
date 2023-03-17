package ilib.gui.comp;

import ilib.gui.IGui;
import ilib.gui.util.Align;
import ilib.util.MCTexts;

import javax.annotation.Nullable;
import java.awt.*;

import static ilib.ClientProxy.mc;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GText extends Component {
	protected static final Color COLOR_DEFAULT = new Color(4210752);

	protected String text;
	protected Color color;
	protected Align align = Align.LEFT;

	public GText(IGui parent, int x, int y, String text, @Nullable Color color) {
		super(parent, x, y);
		this.text = MCTexts.format(text);
		this.color = color == null ? COLOR_DEFAULT : color;
	}

	public static GText alignCenterX(IGui parent, int y, String text, @Nullable Color color) {
		GText c = new GText(parent, 0, y, text, color);
		c.xPos = (parent.getWidth() - c.getWidth()) / 2;
		return c;
	}

	/*******************************************************************************************************************
	 * Overrides                                                                                                       *
	 *******************************************************************************************************************/

	@Override
	public void render(int mouseX, int mouseY) {
		int w = getWidth();

		int x = xPos;

		switch (align) {
			case RIGHT:
				x -= w;
				break;
			case CENTER:
				x -= w / 2;
				break;
		}

		mc.fontRenderer.drawString(text, x, yPos, color.getRGB());
	}

	@Override
	public void render2(int mouseX, int mouseY) {}

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	@Override
	public int getWidth() {
		return MCTexts.getStringWidth(text);
	}

	@Override
	public int getHeight() {
		return 7;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}
}
