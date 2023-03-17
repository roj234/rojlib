package ilib.util;

import roj.collect.AbstractIterator;
import roj.collect.MyHashSet;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

/**
 * @author Roj233
 * @since 2022/5/13 16:51
 */
public abstract class BlockWalker {
	protected final MyHashSet<BlockPos> open1 = new MyHashSet<>(), open2 = new MyHashSet<>();
	public final MyHashSet<BlockPos> closed = new MyHashSet<>();
	private final AbstractIterator<BlockPos> o1Itr, o2Itr;

	protected final BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();

	public BlockWalker() {
		o1Itr = open1.setItr();
		o2Itr = open2.setItr();
	}

	protected long startTime;

	private byte finish;

	public boolean isFinished() {
		return finish != 2;
	}

	public boolean isSucceed() {
		return finish == 0;
	}

	public void pause() {
		finish = 2;
	}

	public boolean walk(BlockPos start) {
		startTime = System.currentTimeMillis();

		MyHashSet<BlockPos> curr = open1;
		MyHashSet<BlockPos> next = open2;
		MyHashSet<BlockPos> done = closed;

		AbstractIterator<BlockPos> cItr = o1Itr;
		AbstractIterator<BlockPos> nItr = o2Itr;

		if (finish != 2) {
			curr.clear();
			next.clear();
			done.clear();
			curr.add(start);

			cItr.reset();
			nItr.reset();

			reset(start);
		} else {
			if (start != null) throw new IllegalStateException("To unpause, parameter must be null");
		}
		finish = 1;

		int i = 0;
		while (canWalk(i++)) {
			if (curr.isEmpty()) {
				finish = 0;
				return true;
			}

			while (cItr.hasNext()) {
				BlockPos pos = cItr.next();
				addNear(next, pos);
				if (2 == finish || shouldPause(curr, next)) {
					finish = 2;
					return false;
				}
			}

			nItr.reset();
			while (nItr.hasNext()) {
				BlockPos p = nItr.next();
				if (!done.add(p)) {
					release0(p);
					nItr.remove();
				}
			}

			MyHashSet<BlockPos> tmp = curr;
			curr = next;
			next = tmp;
			next.clear();

			AbstractIterator<BlockPos> tmp2 = cItr;
			cItr = nItr;
			nItr = tmp2;

			cItr.reset();
		}
		finish = 1;
		return false;
	}

	protected void reset(BlockPos start) {}

	protected boolean shouldPause(MyHashSet<BlockPos> curr, MyHashSet<BlockPos> next) {
		return false;
	}

	protected void release0(BlockPos p) {}

	public MyHashSet<BlockPos> getResult() {
		return this.closed;
	}

	protected void addNear(Set<BlockPos> list, BlockPos pos) {
		BlockPos.PooledMutableBlockPos tmp = BlockPos.PooledMutableBlockPos.retain();
		for (EnumFacing facing : EnumFacing.VALUES) {
			if (isValidPos(tmp.setPos(pos).move(facing), facing)) {
				list.add(tmp.toImmutable());
			}
		}
		tmp.release();
	}

	protected abstract boolean canWalk(int cycle);

	protected abstract boolean isValidPos(BlockPos pos, EnumFacing from);
}
