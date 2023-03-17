package ilib.asm.nx.client;

import ilib.util.PlayerUtil;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.NiximSystem;
import roj.asm.nixim.Shadow;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.resources.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntitySign;

import java.awt.*;
import java.awt.datatransfer.*;

/**
 * @author solo6975
 * @since 2022/5/2 23:22
 */
@Nixim("/")
class NxEditSign extends GuiEditSign {
	@Shadow
	private TileEntitySign tileSign;

	public NxEditSign() {
		super(null);
	}

	@Inject(value = "/", at = Inject.At.TAIL)
	public void initGui() {
		this.addButton(new GuiButton(1, this.width / 2 - 210, this.height / 4 + 120, 20, 20, I18n.format("ilib.copy")));
	}

	@Inject(value = "/", at = Inject.At.HEAD)
	protected void actionPerformed(GuiButton btn) {
		if (btn.id == 1) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 4; i++) {
				sb.append(tileSign.signText[i].getUnformattedText()).append('\n');
			}
			sb.setLength(sb.length() - 1);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
			btn.displayString = I18n.format("ilib.copied");
			return;
		}
		NiximSystem.SpecMethods.$$$VALUE_V();
	}

	@Inject(value = "/", at = Inject.At.HEAD)
	public void onGuiClosed() {
		MinecraftServer srv = PlayerUtil.getMinecraftServer();
		if (srv != null) {
			TileEntitySign sign = (TileEntitySign) srv.getWorld(mc.world.provider.getDimension()).getTileEntity(tileSign.getPos());
			if (sign == null) return;
			sign.setEditable(true);
			sign.setPlayer(srv.getPlayerList().getPlayerByUUID(mc.player.getUniqueID()));
		}
		NiximSystem.SpecMethods.$$$VALUE_V();
	}
}
