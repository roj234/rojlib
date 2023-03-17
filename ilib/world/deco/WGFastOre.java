package ilib.world.deco;

import com.google.common.base.Predicate;
import roj.concurrent.OperationDone;
import roj.math.Vec3i;
import roj.math.VecIterators;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2022/4/28 23:03
 */
public class WGFastOre extends WorldGenerator implements Consumer<Vec3i> {
	IBlockState oreBlock;
	int numberOfBlocks;
	Predicate<IBlockState> predicate;
	VecIterators.MH3 mh3 = VecIterators.mhIterator3D(0, 0, 0, 0);

	public WGFastOre(IBlockState state, int veinSize) {
		this(state, veinSize, StonePredicate.INSTANCE);
	}

	public WGFastOre(IBlockState state, int veinSize, Predicate<IBlockState> pred) {
		this.oreBlock = state;
		this.numberOfBlocks = veinSize;
		this.predicate = pred;
	}

	public boolean generate(World worldIn, Random rand, BlockPos position) {
		VecIterators.MH3 itr = this.mh3;

		world = worldIn;
		rnd = rand;
		cnt = numberOfBlocks;
		itr.x = position.getX();
		itr.y = position.getY();
		itr.z = position.getZ();
		itr.r = 4;
		try {
			itr.forEach(this);
		} catch (OperationDone ignored) {}
		return true;
	}

	int cnt;
	World world;
	Random rnd;
	BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();

	@Override
	public void accept(Vec3i i) {
		if (rnd.nextFloat() < 0.33f) return;

		Chunk c = world.getChunkProvider().getLoadedChunk(i.x >> 4, i.z >> 4);
		if (c == null) return;

		IBlockState state = c.getBlockState(tmp.setPos(i.x, i.y, i.z));
		if (state.getBlock().isReplaceableOreGen(state, world, tmp, predicate)) {
			world.setBlockState(tmp, oreBlock, 18);
		}
		if (--cnt == 0) throw OperationDone.INSTANCE;
	}
}