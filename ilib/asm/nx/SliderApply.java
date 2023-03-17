package ilib.asm.nx;

import ilib.ClientProxy;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.math.MathHelper;

/**
 * @author Roj234
 * @since 2020/8/18 11:59
 */
@Nixim("net.minecraft.client.gui.GuiOptionSlider")
class SliderApply extends GuiButton {

	@Shadow("field_146134_p")
	private float sliderValue;
	@Shadow("field_146135_o")
	public boolean dragging;
	@Copy
	public boolean origDragging;
	@Shadow("field_146133_q")
	private GameSettings.Options options;

	public SliderApply(int _lvt_1_, int _lvt_2_, int _lvt_3_) {
		super(0, 0, 0, "");
	}

	@Copy
	public void func_191745_a(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
		super.drawButton(mc, mouseX, mouseY, partialTicks);
		if (origDragging != dragging) {
			if (origDragging) {
				ClientProxy.mc.gameSettings.setOptionFloatValue(this.options, this.options.denormalizeValue(this.sliderValue));
				this.displayString = mc.gameSettings.getKeyBinding(this.options);
			}
			origDragging = dragging;
		}
	}

	@Inject("func_146119_b")
	protected void mouseDragged(Minecraft mc, int mouseX, int mouseZ) {
		if (this.visible) {
			if (this.dragging) {
				float sliderValue = (float) (mouseX - (this.x + 4)) / (float) (this.width - 8);
				sliderValue = MathHelper.clamp(sliderValue, 0.0F, 1.0F);
				this.sliderValue = this.options.normalizeValue(this.options.denormalizeValue(sliderValue));
			}

			mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			this.drawTexturedModalRect(this.x + (int) (this.sliderValue * (float) (this.width - 8)), this.y, 0, 66, 4, 20);
			this.drawTexturedModalRect(this.x + (int) (this.sliderValue * (float) (this.width - 8)) + 4, this.y, 196, 66, 4, 20);
		}
	}

	@Inject("func_146116_c")
	public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
		if (super.mousePressed(mc, mouseX, mouseY)) {
			float sliderValue = (float) (mouseX - (this.x + 4)) / (float) (this.width - 8);
			this.sliderValue = MathHelper.clamp(sliderValue, 0.0F, 1.0F);
			this.dragging = true;
			return true;
		} else {
			this.dragging = false;
			return false;
		}
	}
}
