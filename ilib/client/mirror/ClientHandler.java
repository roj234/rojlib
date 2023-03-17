package ilib.client.mirror;

import ilib.ClientProxy;
import roj.collect.IntMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj233
 * @since 2022/4/28 13:11
 */
public class ClientHandler {
	private static final IntMap<WorldClient> worlds = new IntMap<>();

	public static void clearWorlds() {
		worlds.clear();
	}

	public static boolean removeWorld(int dimensionId) {
		return worlds.remove(dimensionId) != null;
	}

	@SideOnly(Side.CLIENT)
	public static WorldClient createWorld(int dimensionId) {
		WorldClient w = worlds.get(dimensionId);
		if (w != null) return w;

		Minecraft mc = ClientProxy.mc;
		NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getGameProfile().getId());

		w = new WorldClient(mc.getConnection(), new WorldSettings(0, GameType.SURVIVAL, false, false, WorldType.DEFAULT), dimensionId, EnumDifficulty.NORMAL, mc.profiler);
		worlds.putInt(dimensionId, w);
		return w;
	}

	public static WorldClient getWorld(int dimensionId) {
		return worlds.get(dimensionId);
	}
}
