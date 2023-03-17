package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

/**
 * @author Roj233
 * @since 2022/5/19 23:49
 */
@Nixim("net.minecraft.util.StringUtils")
class NoStripColor {
	@Inject
	public static String stripControlCodes(String text) {
		return text;
	}
}

