package ilib.gui;

import ilib.ImpLib;
import ilib.gui.util.NinePatchRenderer;
import ilib.gui.util.Sprite;

import net.minecraft.util.ResourceLocation;

/**
 * @author Roj233
 * @since 2022/4/17 17:54
 */
public enum DefaultSprites implements Sprite {
	PLAYER_INVENTORY(80, 168, 176, 88),

	SLOT(87, 173, 18, 18), SLOT_OUTPUT(41, 0, 26, 26),

	L_BTN(15, 0, 14, 22), L_BTN_HL(15, 22, 14, 22),

	R_BTN(27, 0, 14, 22), R_BTN_HL(27, 22, 14, 22),

	UP_BTN(0, 24, 14, 8), UP_BTN_HL(0, 32, 14, 8),

	TOGGLE_BTN(67, 0, 32, 14), TOGGLE_BTN_HL(67, 14, 32, 14), TOGGLE_BTN_ON(67, 28, 32, 14), TOGGLE_BTN_ON_HL(67, 42, 32, 14),

	PROGRESS(115, 36, 22, 15), PROGRESS_OVERLAY(137, 36, 22, 16),

	FIRE(53, 26, 14, 14), FIRE_OVERLAY(53, 40, 14, 14),

	MY_FLUID_BACKGROUND(50, 62, 18, 54), MY_FLUID_FOREGROUND(69, 64, 14, 50, 2, 2),

	MY_BACKGROUND(52, 64, 14, 50), MY_WATER_BACKGROUND(84, 65, 12, 48, 1, 1), MY_WATER_FOREGROUND(97, 65, 12, 48, 1, 1), MY_FLUX_BACKGROUND(110, 65, 12, 48, 1, 1),
	MY_FLUX_FOREGROUND(123, 65, 12, 48, 1, 1),

	OLD_FLUID_FOREGROUND(34, 55, 18, 61), OLD_ME_BACKGROUND(0, 52, 18, 64), OLD_ME_BAR(18, 54, 16, 62, 1, 1),

	DARK_SLOT(14, 116, 18, 18), DARK_SLOT_OUTPUT(51, 116, 28, 28),
	;

	public static final ResourceLocation TEXTURE = new ResourceLocation(ImpLib.MODID, "textures/gui/components.png");

	/**
	 * 按钮: 垂直排列的[normal, highlighted, pressed(toggled), disabled] 四个材质 <br>
	 * 可以覆盖 {@link ilib.gui.comp.GButtonNP#setButtonUV(int)} 来修改
	 */
	public static final NinePatchRenderer BUTTON_A = new NinePatchRenderer(0, 0, 2, TEXTURE),
		BUTTON_B = new NinePatchRenderer(99, 0, 2, 12, TEXTURE);

	/**
	 * 原版风格的槽, 任意大小
	 */
	public static final NinePatchRenderer MC_SLOT_NPR = new NinePatchRenderer(8, 19, 1, TEXTURE);
	public static final NinePatchRenderer DARK_SLOT_NPR = new NinePatchRenderer(21, 116, 4, TEXTURE);
	public static final NinePatchRenderer DARK_BG_NPR = new NinePatchRenderer(21, 128, 4, TEXTURE);

	/**
	 * 原版风格的GUI, 任意大小
	 */
	public static final NinePatchRenderer MC_GUI = new NinePatchRenderer(41, 26, 4, TEXTURE);
	public static final NinePatchRenderer DARK_GUI = new NinePatchRenderer(0, 116, 7, TEXTURE);

	static {
		MC_GUI.setRenderFlag(NinePatchRenderer.NO_D | NinePatchRenderer.NO_LD | NinePatchRenderer.NO_RD);
		// 不过下面的部分有
		DARK_GUI.setRenderFlag(NinePatchRenderer.NO_D | NinePatchRenderer.NO_LD | NinePatchRenderer.NO_RD);
	}

	/**
	 * 渲染Tab用, 垂直排列的[normal, reverse(180)]
	 *
	 * @see ilib.gui.comp.GTab
	 * @see ilib.gui.comp.GReverseTab
	 */
	public static final NinePatchRenderer TAB = new NinePatchRenderer(6, 0, 3, TEXTURE);

	private final int u, v, w, h, x, y;

	DefaultSprites(int u, int v, int w, int h) {
		x = y = 0;
		this.u = u;
		this.v = v;
		this.w = w;
		this.h = h;
	}

	DefaultSprites(int u, int v, int w, int h, int x, int y) {
		this.u = u;
		this.v = v;
		this.w = w;
		this.h = h;
		this.x = x;
		this.y = y;
	}

	@Override
	public ResourceLocation texture() {
		return TEXTURE;
	}

	@Override
	public int offsetX() {
		return x;
	}

	@Override
	public int offsetY() {
		return y;
	}

	@Override
	public int u() {
		return u;
	}

	@Override
	public int v() {
		return v;
	}

	@Override
	public int w() {
		return w;
	}

	@Override
	public int h() {
		return h;
	}
}
