package ilib.asm.nx.client;

import ilib.asm.util.XiBaoHelper;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Nixim;

import net.minecraft.client.gui.GuiDisconnected;

@Nixim("/")
class XiBao extends GuiDisconnected {
	public XiBao() {
		super(null, null, null);
	}

	@Override
	@Copy
	public void drawDefaultBackground() {
		XiBaoHelper.drawXiBao(width, height);
	}
}
