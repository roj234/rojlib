package ilib.asm.nx.client;

import ilib.asm.util.XiBaoHelper;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.client.gui.GuiGameOver;

@Nixim("/")
class XiBao2 extends GuiGameOver {
	public XiBao2() {
		super(null);
	}

	@Inject(at = Inject.At.INVOKE, param = {"func_73733_a", "drawXiBao"})
	public void drawScreen(int x, int y, float t) {}

	@Copy
	private static void drawXiBao(GuiGameOver gui, int a, int b, int c, int d, int e, int f) {
		XiBaoHelper.drawXiBao(gui.width, gui.height);
	}
}
