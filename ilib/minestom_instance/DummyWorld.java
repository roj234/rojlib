package ilib.minestom_instance;

import roj.collect.LFUCache;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.village.VillageCollection;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraft.world.storage.SaveHandlerMP;

import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;

/**
 * @author solo6975
 * @since 2022/5/21 23:11
 */
public class DummyWorld extends WorldServer {
	private final WorldServer delegate;

	public DummyWorld(MinecraftServer server, WorldServer overworld, Profiler profilerIn) {
		super(server, new SaveHandlerMP(), new DerivedWorldInfo(overworld.getWorldInfo()), 0, profilerIn);
		delegate = overworld;
	}

	private static WorldServer dummy1;

	public static WorldServer getInstance() {
		if (dummy1 != null) return dummy1;
		WorldServer overworld = DimensionManager.getWorld(0);
		MinecraftServer server = overworld.getMinecraftServer();
		WorldServer dummy = new DummyWorld(server, overworld, new Profiler()).init();
		dummy.getWorldInfo().setGameType(GameType.ADVENTURE);
		return dummy1 = dummy;
	}

	@Override
	protected IChunkProvider createChunkProvider() {
		return new IChunkProvider() {
			final LFUCache<Long, Chunk> loadedChunks = new LFUCache<>(1000);

			@Nullable
			@Override
			public Chunk getLoadedChunk(int x, int z) {
				return loadedChunks.get(ChunkPos.asLong(x, z));
			}

			@Override
			public Chunk provideChunk(int x, int z) {
				Chunk c = loadedChunks.get(ChunkPos.asLong(x, z));
				return c != null ? c : getGlassChunkFor(x, z);
			}

			private Chunk getGlassChunkFor(int x, int z) {
				Chunk glassChunk = new DummyChunk(DummyWorld.this, x, z);
				loadedChunks.put(ChunkPos.asLong(x, z), glassChunk);
				return glassChunk;
			}

			@Override
			public boolean tick() {
				return false;
			}

			@Override
			public String makeString() {
				return "Dummy";
			}

			@Override
			public boolean isChunkGeneratedAt(int x, int z) {
				return true;
			}
		};
	}

	@Override
	public void tick() {
		this.setSkylightSubtracted(15);
	}

	@Override
	public void addBlockEvent(BlockPos pos, Block blockIn, int eventID, int eventParam) {}

	@Override
	public void neighborChanged(BlockPos pos, final Block blockIn, BlockPos fromPos) {}

	@Override
	public void scheduleBlockUpdate(BlockPos pos, Block blockIn, int delay, int priority) {}

	@Override
	public void scheduleUpdate(BlockPos pos, Block blockIn, int delay) {}

	@Override
	public void markAndNotifyBlock(BlockPos pos, @Nullable Chunk chunk, IBlockState iblockstate, IBlockState newState, int flags) {}

	@Override
	public IBlockState getBlockState(BlockPos pos) {
		return Blocks.GLASS.getDefaultState();
	}

	@Override
	public boolean setBlockState(BlockPos pos, IBlockState newState, int flags) {
		return false;
	}

	@Override
	public DummyWorld init() {
		this.mapStorage = delegate.getMapStorage();
		this.worldScoreboard = delegate.getScoreboard();
		this.lootTable = delegate.getLootTableManager();
		this.advancementManager = delegate.getAdvancementManager();
		this.villageCollection = new VillageCollection("SMP");
		return this;
	}
}
