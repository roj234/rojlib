package ilib.asm.nx.trade;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class DistanceUtil {
	public static int maxLivingEntityTickDistanceY;
	public static int maxLivingEntityTickDistanceSquare;
	public static int maxLivingEntitySpawnDistanceY;
	public static int maxLivingEntitySpawnDistanceSquare;

	public static boolean isInClaimedChunk(World world, BlockPos pos) {
		//if (!FTBChunksAPI.isManagerLoaded()) return true;

		//ClaimedChunk chunk = FTBChunksAPI.posManager().posChunk(new ChunkDimPos(world, pos));

		Object chunk = null;
		return chunk != null;
	}

	public static boolean isNearPlayer(World world, BlockPos pos, int maxHeight, int maxDistanceSquare) {
		return isNearPlayer(world, pos.getX(), pos.getY(), pos.getZ(), maxHeight, maxDistanceSquare);
	}

	private static boolean isNearPlayer(World world, double posx, double posy, double posz, int maxHeight, int maxDistanceSquare) {
		List<? extends EntityPlayer> players = world.playerEntities;

		for (int i = 0; i < players.size(); i++) {
			EntityPlayer player = players.get(i);
			if (Math.abs(player.posY - posy) < maxHeight) {
				double x = player.posX - posx;
				double z = player.posZ - posz;

				if (x * x + z * z < maxDistanceSquare) return true;
			}
		}

		return false;
	}
}
