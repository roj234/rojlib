package ilib.gui.comp;

import ilib.ClientProxy;
import ilib.client.RenderUtils;
import ilib.gui.DefaultSprites;
import ilib.gui.GuiHelper;
import ilib.gui.IGui;
import ilib.gui.util.ComponentListener;
import ilib.gui.util.NinePatchRenderer;
import ilib.util.MCTexts;
import roj.math.MathUtils;

import net.minecraft.client.gui.FontRenderer;

import java.awt.*;

import static ilib.gui.comp.GButton.BUTTON_ENABLED;

/**
 * @author Roj233
 * @since 2022/4/10 11:46
 */
public class GSlider extends SimpleComponent {
	protected String label;
	protected Color color = Color.WHITE;
	protected byte flag = BUTTON_ENABLED;

	protected float percent;

	private float prevPercent;
	private int prevX;

	protected NinePatchRenderer buttonModel = DefaultSprites.BUTTON_B;

	public GSlider(IGui parent, int x, int y, int w, int h, float percent) {
		super(parent, x, y, w, h);
		setPercent(percent);
	}

	@Override
	public void mouseDown(int mouseX, int mouseY, int button) {
		super.mouseDown(mouseX, mouseY, button);

		if ((flag & BUTTON_ENABLED) == 0) return;

		prevPercent = percent;
		prevX = mouseX - xPos;
	}

	@Override
	public void mouseDrag(int x, int y, int button, long time) {
		super.mouseDrag(x, y, button, time);

		if ((flag & BUTTON_ENABLED) == 0) return;

		percent = (float) MathUtils.clamp(prevPercent + (float) (x - prevX) / (width - 6), 0, 1);
		updateLabel(percent);
	}

	@Override
	public void mouseUp(int x, int y, int button) {
		super.mouseUp(x, y, button);

		percent = validateValue(percent);
		updateLabel(percent);
		if (prevPercent != percent) {
			acceptValue(percent);

			GuiHelper.playButtonSound();
		}
	}

	@Override
	public void render(int mouseX, int mouseY) {
		RenderUtils.bindTexture(getTexture());
		buttonModel.render(xPos, yPos, 0, 3 * buttonModel.getTextureSize(), width, height);

		int x = (int) ((width - 6) * percent + xPos);
		buttonModel.render(x, yPos, 0, 0, 6, height);
	}

	@Override
	public void render2(int mouseX, int mouseY) {
		if (label != null) {
			FontRenderer fr = ClientProxy.mc.fontRenderer;

			String label = this.label;

			int size = MCTexts.getStringWidth(label);

			fr.drawString(label, xPos + (width - size) / 2f, yPos + (height - fr.FONT_HEIGHT) / 2f, color.getRGB(), false);
		}
	}

	protected void acceptValue(float percent) {
		if (listener != null) listener.actionPerformed(this, ComponentListener.SLIDER_MOVED);
	}

	protected float validateValue(float percent) {
		return percent;
	}

	protected void updateLabel(float percent) {
		label = Float.toString(percent);
	}

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	public final boolean isEnabled() {
		return (flag & BUTTON_ENABLED) != 0;
	}

	public final GSlider setEnabled(boolean enabled) {
		if (enabled) {flag |= BUTTON_ENABLED;} else flag &= ~BUTTON_ENABLED;
		return this;
	}

	public float getPercent() {
		return percent;
	}

	public void setPercent(float percent) {
		this.percent = validateValue(percent);
		updateLabel(percent);
	}

	public NinePatchRenderer getButtonModel() {
		return buttonModel;
	}

	public void setButtonModel(NinePatchRenderer buttonModel) {
		this.buttonModel = buttonModel;
	}

	public String getLabel() {
		return String.valueOf(label);
	}

	public void setLabel(String label) {
		this.label = label != null ? MCTexts.format(label) : null;
	}

	public Color getColor() {
		return color;
	}

	public GSlider setColor(Color color) {
		this.color = color;
		return this;
	}
}
