package ilib.asm.nx.client;

import ilib.asm.util.XiBaoHelper;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MusicTicker;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;

@Nixim("/")
class XiBao3 extends Minecraft {
	XiBao3() {
		super(null);
	}

	@Inject
	public MusicTicker.MusicType getAmbientMusicType() {
		if (this.currentScreen instanceof GuiWinGame) {
			return MusicTicker.MusicType.CREDITS;
		} else if (this.player != null) {
			MusicTicker.MusicType type = this.world.provider.getMusicType();
			if (type != null) {
				return type;
			} else if (this.player.world.provider instanceof WorldProviderHell) {
				return MusicTicker.MusicType.NETHER;
			} else if (this.player.world.provider instanceof WorldProviderEnd) {
				return this.ingameGUI.getBossOverlay().shouldPlayEndBossMusic() ? MusicTicker.MusicType.END_BOSS : MusicTicker.MusicType.END;
			} else {
				return this.player.capabilities.isCreativeMode && this.player.capabilities.allowFlying ? MusicTicker.MusicType.CREATIVE : MusicTicker.MusicType.GAME;
			}
		} else {
			return XiBaoHelper.isXibao() ? XiBaoHelper.XIBAO : MusicTicker.MusicType.MENU;
		}
	}
}
