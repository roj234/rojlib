package ilib.client.music;

import ilib.anim.Animation;
import ilib.anim.Keyframe;
import ilib.anim.Keyframes;
import ilib.anim.timing.TFRegistry;
import ilib.gui.GuiBaseNI;
import ilib.gui.IGui;
import ilib.gui.comp.Component;
import ilib.gui.comp.*;
import ilib.gui.util.ComponentListener;
import ilib.gui.util.Direction;

import net.minecraft.client.Minecraft;

import java.awt.*;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/8/10 16:08
 */
public class GuiFunction extends GuiBaseNI implements ComponentListener {
	private static final Keyframes xRotateIn;

	static {
		xRotateIn = new Keyframes("RotateInLeft");

		Keyframe kf;
		kf = new Keyframe(0);
		kf.rotate(0, 0, 1, (float) Math.toRadians(-90));
		xRotateIn.add(kf);

		kf = new Keyframe(10);
		xRotateIn.add(kf);
	}

	static String filter;

	public GuiFunction() {
		super(-1, -1, Component.TEXTURE);
		this.prevScreen = Minecraft.getMinecraft().currentScreen;
		filter = "";
	}

	@Override
	protected void addComponents() {
		components.add(GText.alignCenterX(this, 10, "ImpLib扩展功能", Color.WHITE));

		components.add(new FunctionList(this, 10, 24, -10, -54).setDirectionAndInit(Direction.RIGHT));

		components.add(new GTextInput(this, 20, 10, 120, 14, "").setMark(1));

		components.add(new GButtonNP(this, 0, 0, "返回").setMark(2));

		Animation rotateIn = new Animation(xRotateIn, TFRegistry.LINEAR);
		rotateIn.setDuration(500);
		rotateIn.play();

		for (int i = 0; i < components.size(); i++) {
			components.get(i).setAnimation(rotateIn);
		}
	}

	@Override
	public void getDynamicTooltip(Component c, List<String> tooltip, int mouseX, int mouseY) {
		if (c.getMark() == 1 && ((GTextInput) c).getText().isEmpty()) {
			tooltip.add("输入以搜索");
		}
	}

	@Override
	public void actionPerformed(Component c, int action) {
		if (c.getMark() == 2) {
			mc.displayGuiScreen(prevScreen);
		} else if (c.getMark() == 1) {
			GTextInput search = (GTextInput) c;
			filter = search.getText().toLowerCase();
			((FunctionList) components.get(1)).refresh();
		}
	}

	private static class FunctionList extends GScrollView implements ComponentListener {
		public FunctionList(IGui parent, int x, int y, int w, int h) {
			super(parent, x, y, w, h);
			alwaysShow = true;
			setStep(3);
		}

		@Override
		protected void addElements(int from, int to) {
			int y = 4;
			components.add(new GButtonNP(this, -48, y, 32, 32, "WIP"));
		}

		@Override
		protected int getElementCount() {
			return 100;
		}

		@Override
		protected int getElementLength() {
			return 20;
		}

		@Override
		public void actionPerformed(Component c, int action) {
			int id = components.indexOf(c) - reserved;
			if (id >= 0) {

			}
		}
	}
}
