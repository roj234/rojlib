package ilib.asm.nx;

import com.mojang.authlib.GameProfile;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;

import java.net.SocketAddress;
import java.util.UUID;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.server.management.PlayerList")
abstract class NoDuplicateLogin extends PlayerList {
	public NoDuplicateLogin(MinecraftServer serv) {
		super(serv);
	}

	@Inject("func_148542_a")
	public String allowUserToConnect(SocketAddress address, GameProfile profile) {
		String result = super.allowUserToConnect(address, profile);
		if (result != null) return result;
		UUID uuid = EntityPlayer.getUUID(profile);
		if (getPlayerByUUID(uuid) != null) {
			return "抱歉，对方已登录";
		}
		return null;
	}
}
