package ilib.asm.nx;

import com.mojang.authlib.GameProfile;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;

/**
 * @author solo6975
 * @since 2022/6/9 1:11
 */
@Nixim("net.minecraft.entity.player.EntityPlayerMP")
class NoJoinSpawn {
	@Inject(value = "<init>", at = Inject.At.INVOKE, param = {"getRandomizedSpawnPoint", "redirect__getRandomizedSpawnPoint"})
	public void init(MinecraftServer a, WorldServer b, GameProfile c, PlayerInteractionManager d) {}

	@Copy(unique = true)
	private static BlockPos redirect__getRandomizedSpawnPoint(WorldProvider wp) {
		return BlockPos.ORIGIN;
	}
}
