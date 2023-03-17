package ilib.asm.nx;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import ilib.Config;
import ilib.asm.util.AsyncChunkGenerator;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.MyBitSet;
import roj.concurrent.task.AsyncTask;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.network.play.server.SPacketUnloadChunk;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.chunkio.ChunkIOExecutor;
import net.minecraftforge.event.world.ChunkWatchEvent;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author solo6975
 * @since 2022/5/22 0:16
 */
@Nixim(value = "/", copyItf = true)
class NxAsyncPCME extends PlayerChunkMapEntry implements Runnable {
	@Copy
	private MyBitSet changedBlocks2;
	@Copy
	public AsyncTask<Chunk> asyncGenerateTask;

	@Shadow
	private PlayerChunkMap playerChunkMap;
	@Shadow
	private List<EntityPlayerMP> players;
	@Shadow
	private ChunkPos pos;
	@Shadow
	private short[] changedBlocks;
	@Shadow
	@Nullable
	private Chunk chunk;
	@Shadow
	private int changes;
	@Shadow
	private int changedSectionFilter;
	@Shadow
	private boolean sentToPlayers;
	@Shadow("/")
	private boolean loading;

	@Override
	@Copy
	public void run() {
		chunk = playerChunkMap.getWorldServer().getChunkProvider().loadChunk(pos.x, pos.z);
		loading = false;
	}

	@Shadow("func_187273_a")
	private void sendBlockEntity(TileEntity be) {}

	NxAsyncPCME() {
		super(null, 0, 0);
	}

	@Inject(value = "<init>", at = Inject.At.REPLACE)
	public void new1(PlayerChunkMap owner, int chunkX, int chunkZ) {
		$$$CONSTRUCTOR();
		players = Lists.newArrayList();
		changedBlocks = new short[64];
		playerChunkMap = owner;
		pos = new ChunkPos(chunkX, chunkZ);
		loading = true;

		ChunkProviderServer cp = owner.getWorldServer().getChunkProvider();
		if (cp.isChunkGeneratedAt(chunkX, chunkZ)) {
			cp.loadChunk(chunkX, chunkZ, this);
		} else {
			loading = false;
		}
	}

	private void $$$CONSTRUCTOR() {}

	@Inject("/")
	public void removePlayer(EntityPlayerMP player) {
		if (players.remove(player)) {
			if (chunk == null) {
				if (players.isEmpty()) {
					if (loading) {
						ChunkIOExecutor.dropQueuedChunkLoad(playerChunkMap.getWorldServer(), pos.x, pos.z, this);
					}
					if (asyncGenerateTask != null) {
						asyncGenerateTask.cancel(true);
					}
					playerChunkMap.removeEntry(this);
				}
				return;
			}

			if (sentToPlayers) {
				player.connection.sendPacket(new SPacketUnloadChunk(pos.x, pos.z));
			}
			MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.UnWatch(chunk, player));
			if (players.isEmpty()) {
				playerChunkMap.removeEntry(this);
			}
		}
	}

	@Inject("/")
	public boolean providePlayerChunk(boolean canGenerate) {
		if (loading) return false;
		if (chunk != null) return true;
		ChunkProviderServer cp = playerChunkMap.getWorldServer().getChunkProvider();

		if (asyncGenerateTask != null) {
			if (!asyncGenerateTask.isDone()) return false;
			chunk = AsyncChunkGenerator.syncCallback(asyncGenerateTask, pos, cp);
			return true;
		}

		if (canGenerate) {
			if (Config.asyncChunkGen) {
				asyncGenerateTask = AsyncChunkGenerator.createTask(pos, cp);
			} else {
				chunk = cp.provideChunk(pos.x, pos.z);
			}
		} else {
			chunk = cp.loadChunk(pos.x, pos.z);
		}

		return chunk != null;
	}

	@Inject("/")
	public boolean sendToPlayers() {
		if (sentToPlayers) return true;
		if (chunk == null || !chunk.isPopulated()) return false;
		changes = 0;
		changedSectionFilter = 0;
		sentToPlayers = true;

		if (!players.isEmpty()) {
			Packet<?> packet = new SPacketChunkData(chunk, 65535);
			for (int i = 0; i < players.size(); i++) {
				EntityPlayerMP player = players.get(i);
				player.connection.sendPacket(packet);
				playerChunkMap.getWorldServer().getEntityTracker().sendLeashedEntitiesInChunk(player, chunk);
				MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(chunk, player));
			}

		}
		return true;
	}

	@Inject("/")
	public void blockChanged(int x, int y, int z) {
		if (sentToPlayers) {
			if (changes == 0) {
				playerChunkMap.entryChanged(this);
			}

			changedSectionFilter |= 1 << (y >> 4);
			short idx = (short) (x << 12 | z << 8 | y);

			if (changes < 64) {
				for (int i = changes - 1; i >= 0; --i) {
					if (changedBlocks[i] == idx) {
						return;
					}
				}
			} else {
				if (changedBlocks2.contains(idx)) return;
			}

			if (changes == changedBlocks.length) {
				changedBlocks = Arrays.copyOf(changedBlocks, changedBlocks.length << 1);
			}

			if (changes == 63) {
				if (changedBlocks2 == null) changedBlocks2 = new MyBitSet();
				else changedBlocks2.clear();
				for (int i = 0; i < 63; i++) {
					changedBlocks2.add(changedBlocks[i] & 0xFFFF);
				}
			}

			changedBlocks[changes++] = idx;
		}
	}

	@Inject("/")
	public void update() {
		if (!sentToPlayers || chunk == null || changes == 0) return;

		short[] modified = changedBlocks;
		WorldServer world = playerChunkMap.getWorldServer();

		if (changes == 1) {
			int x = (modified[0] >> 12 & 15) + pos.x * 16;
			int y = modified[0] & 255;
			int z = (modified[0] >> 8 & 15) + pos.z * 16;
			BlockPos pos = new BlockPos(x, y, z);

			sendPacket(new SPacketBlockChange(world, pos));
			IBlockState state = world.getBlockState(pos);
			if (state.getBlock().hasTileEntity(state)) {
				sendBlockEntity(world.getTileEntity(pos));
			}
		} else if (changes >= ForgeModContainer.clumpingThreshold) {
			sendPacket(new SPacketChunkData(chunk, changedSectionFilter));
		} else {
			sendPacket(new SPacketMultiBlockChange(changes, modified, chunk));

			BlockPos.PooledMutableBlockPos tmp = BlockPos.PooledMutableBlockPos.retain();
			for (int i = 0; i < changes; ++i) {
				tmp.setPos((modified[i] >> 12 & 15) + pos.x * 16, modified[i] & 255, (modified[i] >> 8 & 15) + pos.z * 16);
				IBlockState state = world.getBlockState(tmp);
				if (state.getBlock().hasTileEntity(state)) {
					sendBlockEntity(world.getTileEntity(tmp));
				}
			}
			tmp.release();
		}

		changes = 0;
		changedSectionFilter = 0;
	}

	@Inject("/")
	public boolean hasPlayerMatching(Predicate<EntityPlayerMP> pred) {
		for (int i = 0; i < players.size(); i++) {
			if (pred.test(players.get(i))) return true;
		}
		return false;
	}

	@Inject("/")
	public double getClosestPlayerDistance() {
		double min = 1.7976931348623157E308D;
		for (int i = 0; i < players.size(); i++) {
			EntityPlayerMP player = players.get(i);
			double dis = pos.getDistanceSq(player);
			if (dis < min) {
				min = dis;
			}
		}
		return min;
	}

	@Inject("/")
	public List<EntityPlayerMP> getWatchingPlayers() {
		return this.isSentToPlayers() ? players : Collections.emptyList();
	}
}