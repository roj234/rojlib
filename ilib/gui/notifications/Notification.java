package ilib.gui.notifications;

import ilib.ClientProxy;
import ilib.client.RenderUtils;
import ilib.util.MCTexts;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/2 22:@6
 */
public class Notification {
	protected final int x, y;
	protected int width, height;

	protected int life;
	protected int pad, lineHeight;
	protected float scale;

	protected String message;

	public Notification() {
		this.x = 5;
		this.y = 5;
		this.width = 128;
		this.height = 32;
		this.pad = 5;
		this.lineHeight = 10;
		this.scale = 1;

		this.life = 20 * 10;
	}

	public Notification(String message) {
		this();
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void onAdded(int index) {
		this.life = -1;
	}

	public void onRemove() {
		this.life = -1;
	}

	public void draw() {
		GlStateManager.pushMatrix();
		GlStateManager.scale(scale, scale, scale);

		RenderUtils.drawRectangle(x, y, 10, width, height, 0xAA333333);

		FontRenderer fr = ClientProxy.mc.fontRenderer;
		fr.drawString("通知 1 / " + Notifier.activated.size(), (int) ((x + pad - 3) / scale), (int) ((y + pad - 3) / scale), 0x66FFFFFF, false);

		String tick = Integer.toString(life / 20);
		int tickWidth = MCTexts.getStringWidth(tick);
		fr.drawString(tick, (int) ((x + width - pad + 3 - (tickWidth * scale)) / scale), (int) ((y + pad - 3) / scale), 0x66FFFFFF, false);

		List<String> lines = MCTexts.splitByWidth(this.getMessage(), (int) ((width - (pad * 2)) / scale));
		int lineIndex = 0;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			fr.drawString(line, (int) ((x + pad) / scale), (int) ((y + pad) / scale) + (lineHeight * lineIndex) + lineHeight, 0xFFFFFFFF, false);
			lineIndex++;
		}

		this.height = (int) ((pad * 2) + (lineIndex * (lineHeight * scale)) + (lineHeight * scale));

		GlStateManager.popMatrix();
	}

	public boolean isExclusive() {
		return false;
	}
}
