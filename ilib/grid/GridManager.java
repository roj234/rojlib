package ilib.grid;

import ilib.ImpLib;
import ilib.math.FastPath;
import ilib.math.Section;
import ilib.util.DimensionHelper;
import ilib.util.NBTType;
import roj.collect.*;
import roj.math.Rect3i;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj233
 * @since 2022/5/13 16:11
 */
public class GridManager {
	public static final GridManager ME = new GridManager("ME");

	public final String unit;

	private final MyBitSet networkId = new MyBitSet();
	private final SimpleList<Grid> byId = new SimpleList<>();
	private final FastPath<Grid> byPos = new FastPath<>();

	private final FastPath<GridData> prevData = new FastPath<>();

	private final Section tmpSect = new Section();
	private final MyHashSet<Grid> tmpSet = new MyHashSet<>();
	private final AbstractIterator<Grid> tmpItr = tmpSet.setItr();
	private final SimpleList<Grid> tmpList = new SimpleList<>();

	public GridManager(String unit) {
		this.unit = unit;
	}

	public void register(TileEntity tile) {
		if (inFind) return;

		GridEntry entry = getGrid(tile, true);
		if (entry == null) return;

		tmpSet.clear();
		Set<Grid> grids = byPos.getAll(DimensionHelper.idFor(tile.getWorld()), tmpSect.set(tile.getPos(), tile.getPos()).grow(1), tmpSet);

		SimpleList<Grid> toMerge = entry instanceof IConductor ? tmpList : null;
		if (toMerge != null) toMerge.clear();

		boolean added = false;
		AbstractIterator<Grid> it = tmpItr;
		for (it.reset(); it.hasNext(); ) {
			Grid grid = it.next();
			if (added |= grid.addNode(entry)) {
				if (toMerge != null) toMerge.add(grid);
			}
		}

		GridData data = prevData.get(DimensionHelper.idFor(tile.getWorld()), tile.getPos());
		IntSet components;
		if (data != null) {
			components = data.get(tile.getPos());
			if (data.isEmpty()) {
				prevData.remove(data);
			}
		} else {
			components = null;
		}

		if (toMerge != null && toMerge.size() > 1) {
			Grid grid = toMerge.remove(0);
			for (int i = 0; i < toMerge.size(); i++) {
				removeNetwork(toMerge.get(i));
			}
			grid.merge(toMerge);

			if (components != null) {
				grid.readConductors(tile.getWorld().getChunk(tile.getPos()), components);
			}
		} else

		if (!added) {
			System.err.println("new " + entry);

			Grid grid = newNetwork(tile.getWorld());
			if (components != null) {
				grid.readConductors(tile.getWorld().getChunk(tile.getPos()), components);
				return;
			}
			grid.queueRefresh(entry.pos());
		}
	}

	public void unregister(TileEntity tile) {
		GridEntry entry = getGrid(tile, true);
		if (entry == null) return;

		tmpSet.clear();
		Set<Grid> grids = byPos.getAll(DimensionHelper.idFor(tile.getWorld()), tmpSect.set(tile.getPos(), tile.getPos()), tmpSet);

		boolean removed = false;
		AbstractIterator<Grid> it = tmpItr;
		for (it.reset(); it.hasNext(); ) {
			removed |= it.next().removeNode(entry);
		}

		if (!removed) ImpLib.logger().warn("节点不在网络中: {} at {}", tile, tile.getPos());
	}

	protected final GridEntry getGrid(TileEntity tile, boolean log) {
		GridEntry entry = getGrid(tile);
		if (entry == null) {
			if (log) ImpLib.logger().error("无效的注册: {} at {}", tile, tile.getPos());
			return null;
		}
		if (!entry.getEnergyUnit().equals(unit)) {
			GridEntry other = entry.transform(unit);
			if (other == null) {
				if (log) ImpLib.logger().error("无效的能源类型 {}, 需求 {}: {} at {}", entry.getEnergyUnit(), unit, tile, tile.getPos());
				return null;
			}
			return other;
		}
		return entry;
	}

	private Grid newNetwork(World world) {
		Grid grid = new Grid(this, world);
		int id = networkId.first();
		if (id < 0) {
			grid.id = byId.size();
			byId.add(grid);
		} else {
			networkId.remove(id);
			byId.set(grid.id = id, grid);
		}
		return grid;
	}

	void updatePosition(Grid grid) {
		byPos.remove(grid);
		byPos.put(grid);
	}

	private void tickNetwork() {
		SimpleList<Grid> byId = this.byId;
		for (int i = 0; i < byId.size(); i++) {
			Grid grid = byId.get(i);
			if (grid != null) {
				grid.update();
				if (grid.isEmpty()) {
					removeNetwork(grid);
				}
			}
		}
	}

	void removeNetwork(Grid grid) {
		byId.set(grid.id, null);
		byPos.remove(grid);
		networkId.add(grid.id);
	}

	public Set<Grid> getNearbyConnectable(int world, BlockPos pos, Grid exclude) {
		tmpSet.clear();
		Set<Grid> grids = byPos.getAll(world, tmpSect.set(pos, pos).grow(1), tmpSet);
		grids.remove(exclude);
		if (grids.isEmpty()) return Collections.emptySet();

		AbstractIterator<Grid> itr = tmpItr;
		for (itr.reset(); itr.hasNext(); ) {
			Grid grid = itr.next();
			if (!grid.hasNearbyConductor(pos)) {
				itr.remove();
			}
		}
		return grids;
	}

	public Set<Grid> getNearby(int world, BlockPos pos) {
		tmpSet.clear();
		return byPos.getAll(world, tmpSect.set(pos, pos).grow(1), tmpSet);
	}

	public void enable() {
		disable();

		running = true;
		Thread thread = this.thread = new Thread(this::asyncTick);
		thread.setDaemon(true);
		thread.setName(unit + " Grid PathFinder");
		thread.start();

		MinecraftForge.EVENT_BUS.register(this);
	}

	public void disable() {
		byId.clear();
		byPos.clear();
		networkId.clear();

		running = false;
		if (thread != null) {
			LockSupport.unpark(thread);
			try {
				thread.join(20);
			} catch (InterruptedException ignored) {}
			thread.stop();
			if (pathLock.isLocked()) {
				pathLock = new ReentrantLock(true);
			}
		}

		free.addAll(working);
		working.clear();
		workIndex = 0;
	}

	private Thread thread;
	private boolean running;
	private ReentrantLock pathLock = new ReentrantLock(true);

	@SubscribeEvent
	public void onChunkSaveData(ChunkDataEvent.Save event) {
		Chunk chunk = event.getChunk();

		tmpSet.clear();
		byPos.getAll(DimensionHelper.idFor(event.getWorld()), tmpSect.set(chunk.x << 4, 0, chunk.z << 4, (chunk.x + 1) << 4, 256, (chunk.z + 1) << 4), tmpSet);
		if (tmpSet.isEmpty()) return;

		AbstractIterator<Grid> it = tmpItr;
		it.reset();
		event.getData().setTag("ILNet" + unit, GridData.writeToNBT(tmpSect, it));
	}

	@SubscribeEvent
	public void onChunkLoadData(ChunkDataEvent.Load event) {
		NBTTagCompound tag = event.getData();
		if (!tag.hasKey("ILNet" + unit, NBTType.LIST)) return;

		Chunk chunk = event.getChunk();
		Rect3i tmp = tmpSect.set(chunk.x << 4, 0, chunk.z << 4, (chunk.x + 1) << 4, 256, (chunk.z + 1) << 4).copy();
		GridData data = new GridData(DimensionHelper.idFor(event.getWorld()), (Section) tmp);
		data.readFromNBT(tag.getTagList("ILNet" + unit, NBTType.BYTE_ARRAY));
		prevData.put(data);
	}

	@SubscribeEvent
	public void tick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END) return;

		tickNetwork();
	}

	public void asyncTick() {
		while (true) {
			while (working.isEmpty() && running) LockSupport.park();
			if (!running) return;

			tickPathFind(20);
		}
	}

	// region PathFinder

	private final SimpleList<PathFinder> free = new SimpleList<>(), working = new SimpleList<>();
	private int workIndex;
	private volatile boolean inFind;

	PathFinder findPath(World world, BlockPos from) {
		PathFinder pf;

		// free add;remove is on main thread
		if (free.isEmpty()) pf = new PathFinder(this);
		else pf = free.remove(free.size() - 1);

		pf.world = world;
		inFind = true;
		try {
			pf.walk(from);
		} finally {
			inFind = false;
		}

		if (!pf.isFinished()) {
			MyChunkCache mcc = new MyChunkCache();
			mcc.init(world, from.getX() - 128, from.getY() - 128, from.getZ() - 128, from.getX() + 128, from.getY() + 128, from.getZ() + 128);
			pf.world = mcc;

			pathLock.lock();
			working.add(pf);
			pathLock.unlock();
			LockSupport.unpark(thread);
		} else {
			pf.world = null;
			free.add(pf);
		}

		return pf;
	}

	void cancelOrEndTask(PathFinder pf) {
		int pos = working.indexOf(pf);
		if (pos >= 0) {
			pathLock.lock();
			working.remove(pos);
			if (workIndex > pos) workIndex--;
			pathLock.unlock();
		}
		if (free.size() < 20) free.add(pf);
	}

	private void tickPathFind(int timeMaxMs) {
		long deadline = System.nanoTime() + timeMaxMs * 1_000_000;

		SimpleList<PathFinder> working = this.working;
		for (int i = workIndex; ; i++) {
			PathFinder pf;

			inFind = true;
			pathLock.lock();
			try {
				if (i >= working.size()) break;
				pf = working.get(i);

				if (((MyChunkCache) pf.world).isDone()) pf.walk(null);

				if (pf.isFinished()) {
					pf.world = null;
					working.remove(i--);
				}
			} finally {
				inFind = false;
				pathLock.unlock();
			}

			if (!running) break;

			if (System.nanoTime() > deadline) {
				workIndex = i;
				return;
			}
		}
		workIndex = 0;
	}

	// endregion

	public static GridEntry getGrid(TileEntity tile) {
		Capability<GridEntry> cap = null;
		return tile instanceof GridEntry ? (GridEntry) tile : null;//tile.getCapability(cap, null);
	}
}
