package ilib.gui.comp;

import ilib.ClientProxy;
import ilib.gui.IGui;
import ilib.gui.util.Direction;
import ilib.util.MCTexts;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.List;


/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GTextLong extends GScrollView {
	private static final int BTN_U = 42, BTN_V = 122;

	// Variables
	protected int scale;
	protected Color color;
	protected List<String> lines;
	protected String text;

	protected int to;

	public GTextLong(IGui parent, int x, int y, int w, int h, String text, int scale, Color color) {
		this(parent, x, y, w, h, text, scale);
		this.color = color == null ? Color.WHITE : color;
	}

	public GTextLong(IGui parent, int x, int y, int w, int h, String text, int scale) {
		super(parent, x, y, w, h);
		this.scale = scale;
		this.text = text;
		this.color = Color.WHITE;

		setReservedComponentCount(4);
	}

	@Override
	public void onInit() {
		super.onInit();

		if (text != null) {
			setRawLine(text);
			text = null;
		}
		setDirectionAndInit(null);
	}

	@Override
	public GScrollView setDirectionAndInit(Direction d) {
		int maxOnScreen = (int) (((float) height) / ((((float) scale) * 9f) / 100f));

		setStep(MathHelper.clamp(maxOnScreen, 1, 4));
		super.setDirectionAndInit(Direction.RIGHT);
		components.add(new LongTextRenderer());
		return this;
	}

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public int getScale() {
		return scale;
	}

	public void setScale(int scale) {
		this.scale = scale;
		setDirectionAndInit(null);
	}

	public List<String> getLines() {
		return lines;
	}

	public void setLines(List<String> lines) {
		this.lines = lines;
		setDirectionAndInit(null);
	}

	public void setRawLine(String line) {
		line = MCTexts.format(line);

		int lineWidthNoScroll = (int) ((100f / scale) * width);
		lines = MCTexts.splitByWidth(line, lineWidthNoScroll);
		if (lines.size() > (int) getViewCapacity()) {
			int lineWidth = (int) ((100f / scale) * (width - 14f));
			lines = MCTexts.splitByWidth(line, lineWidth);
		}
		setDirectionAndInit(null);
	}

	@Override
	protected void addElements(int from, int to) {
		this.to = to;
	}

	@Override
	protected int getElementCount() {
		return lines == null ? 0 : lines.size();
	}

	@Override
	protected int getElementLength() {
		return (scale * ClientProxy.mc.fontRenderer.FONT_HEIGHT) / 100;
	}

	protected class LongTextRenderer extends SimpleComponent {
		public LongTextRenderer() {
			super(GTextLong.this, GTextLong.this.xPos, GTextLong.this.yPos, GTextLong.this.width - 14, GTextLong.this.height);
		}

		@Override
		public void render2(int mouseX, int mouseY) {
			if (lines == null) return;

			GlStateManager.pushMatrix();

			GlStateManager.translate(4, 4, 0);

			FontRenderer fr = ClientProxy.mc.fontRenderer;

			boolean unicode = fr.getUnicodeFlag();
			fr.setUnicodeFlag(false);

			int yPos = 0;
			int actualY = 0;

			int lineHeight = (scale * fr.FONT_HEIGHT) / 100;

			GlStateManager.scale(scale / 100F, scale / 100F, scale / 100F);
			for (int x = off; x < to; x++) {
				if (actualY + lineHeight > height) break;

				fr.drawString(lines.get(x), 0, yPos, color.getRGB());
				yPos += fr.FONT_HEIGHT;
				actualY += lineHeight;
			}

			fr.setUnicodeFlag(unicode);
			GlStateManager.popMatrix();
		}
	}
}
