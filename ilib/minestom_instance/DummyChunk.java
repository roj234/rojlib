package ilib.minestom_instance;


import com.google.common.base.Predicate;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * @author solo6975
 * @since 2022/5/21 23:31
 */
public class DummyChunk extends Chunk {
	public DummyChunk(World w, int x, int z) {
		super(w, x, z);
	}

	public boolean isAtLocation(int x, int z) {
		return true;
	}

	public int getHeightValue(int x, int z) {
		return 255;
	}

	public void generateHeightMap() {
	}

	public void generateSkylightMap() {
	}

	public IBlockState getBlockState(BlockPos pos) {
		return Blocks.GLASS.getDefaultState();
	}

	public int getBlockLightOpacity(BlockPos pos) {
		return 0;
	}

	public int getLightFor(EnumSkyBlock type, BlockPos pos) {
		return type.defaultLightValue;
	}

	public void setLightFor(EnumSkyBlock type, BlockPos pos, int value) {}

	public int getLightSubtracted(BlockPos pos, int amount) {
		return 0;
	}

	public void addEntity(Entity _lvt_1_) {}

	public void removeEntity(Entity _lvt_1_) {}

	public void removeEntityAtIndex(Entity entityIn, int index) {}

	public boolean canSeeSky(BlockPos pos) {
		return true;
	}

	@Nullable
	public TileEntity getTileEntity(BlockPos pos, EnumCreateEntityType creationMode) {
		return null;
	}

	public void addTileEntity(TileEntity _lvt_1_) {}

	public void addTileEntity(BlockPos _lvt_1_, TileEntity _lvt_2_) {}

	public void removeTileEntity(BlockPos pos) {}

	public void onLoad() {}

	public void onUnload() {}

	public void markDirty() {}

	public void getEntitiesWithinAABBForEntity(@Nullable Entity entityIn, AxisAlignedBB aabb, List<Entity> listToFill, Predicate<? super Entity> filter) {}

	public <T extends Entity> void getEntitiesOfTypeWithinAABB(Class<? extends T> entityClass, AxisAlignedBB aabb, List<T> listToFill, Predicate<? super T> filter) {}

	public boolean needsSaving(boolean now) {
		return false;
	}

	public Random getRandomWithSeed(long seed) {
		return new Random(this.getWorld().getSeed() + (long) (this.x * this.x * 4987142) + (long) (this.x * 5947611) + (long) (this.z * this.z) * 4392871L + (long) (this.z * 389711) ^ seed);
	}

	public boolean isEmpty() {
		return false;
	}

	public boolean isEmptyBetween(int ys, int yt) {
		return false;
	}
}
