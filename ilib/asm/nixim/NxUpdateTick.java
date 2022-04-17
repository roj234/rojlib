package ilib.asm.nixim;

import com.google.common.collect.Lists;
import ilib.ImpLib;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Inject.At;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.BSLowHeap;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import java.util.List;
import java.util.Set;

/**
 * @author Roj233
 * @since 2022/4/17 15:20
 */
@Nixim("net.minecraft.world.WorldServer")
class NxUpdateTick extends WorldServer {
    @Shadow("field_73064_N")
    private Set<NextTickListEntry> pendHashSet;
    @Copy
    private BSLowHeap<NextTickListEntry> myTreeSet;
    @Shadow("field_94579_S")
    private List<NextTickListEntry> pendThisTick;

    @Inject(value = "<init>", at = At.TAIL)
    public NxUpdateTick(MinecraftServer server, ISaveHandler saveHandlerIn, WorldInfo info, int dimensionId, Profiler profilerIn) {
        super(server, saveHandlerIn, info, dimensionId, profilerIn);
        myTreeSet = new BSLowHeap<>(null);
    }

    @Inject("func_175654_a")
    public void updateBlockTick(BlockPos pos, Block blockIn, int delay, int priority) {
        Material material = blockIn.getDefaultState().getMaterial();
        if (scheduledUpdatesAreImmediate && material != Material.AIR) {
            if (blockIn.requiresUpdates()) {
                boolean isForced = getPersistentChunks().containsKey(new ChunkPos(pos));
                int range = isForced ? 0 : 8;
                if (isAreaLoaded(pos.add(-range, -range, -range), pos.add(range, range, range))) {
                    IBlockState state = getBlockState(pos);
                    if (state.getMaterial() != Material.AIR && state.getBlock() == blockIn) {
                        state.getBlock().updateTick(this, pos, state, rand);
                    }
                }

                return;
            }

            delay = 1;
        }

        NextTickListEntry entry = new NextTickListEntry(pos, blockIn);
        if (isBlockLoaded(pos)) {
            if (material != Material.AIR) {
                entry.setScheduledTime((long)delay + worldInfo.getWorldTotalTime());
                entry.setPriority(priority);
            }

            if (pendHashSet.add(entry)) {
                myTreeSet.add(entry);
            }
        }

    }

    @Inject("func_180497_b")
    public void scheduleBlockUpdate(BlockPos pos, Block blockIn, int delay, int priority) {
        if (blockIn != null) {
            NextTickListEntry entry = new NextTickListEntry(pos, blockIn);
            entry.setPriority(priority);

            Material material = blockIn.getDefaultState().getMaterial();
            if (material != Material.AIR) {
                entry.setScheduledTime((long)delay + worldInfo.getWorldTotalTime());
            }

            if (pendHashSet.add(entry)) {
                myTreeSet.add(entry);
            }
        }
    }

    @Inject("func_72955_a")
    public boolean tickUpdates(boolean runAllPending) {
        if (worldInfo.getTerrainType() == WorldType.DEBUG_ALL_BLOCK_STATES) {
            pendHashSet.clear();
            pendThisTick.clear();
            myTreeSet.clear();
            return false;
        }

        int size = myTreeSet.size();
        if (size != pendHashSet.size()) {
            ImpLib.logger().fatal("NextTickList out of synch");
        }

        if (size > 65536) {
            size = 65536;
        }

        profiler.startSection("cleaning");

        int i = 0;
        for(; i < size; ++i) {
            NextTickListEntry entry = myTreeSet.get(i);
            if (!runAllPending && entry.scheduledTime > worldInfo.getWorldTotalTime()) {
                break;
            }

            pendHashSet.remove(entry);
            pendThisTick.add(entry);
        }
        myTreeSet.removeRange(0, i);

        profiler.endSection();
        profiler.startSection("ticking");

        for (i = pendThisTick.size() - 1; i >= 0; i--) {
            NextTickListEntry entry = pendThisTick.remove(i);
            if (isBlockLoaded(entry.position, true)) {
                IBlockState state = getBlockState(entry.position);
                if (state.getMaterial() != Material.AIR && Block.isEqualTo(state.getBlock(), entry.getBlock())) {
                    try {
                        state.getBlock().updateTick(this, entry.position, state, rand);
                    } catch (Throwable e) {
                        CrashReport rpt = CrashReport.makeCrashReport(e, "Exception while ticking a block");
                        CrashReportCategory cat = rpt.makeCategory("Block being ticked");
                        CrashReportCategory.addBlockInfo(cat, entry.position, state);
                        throw new ReportedException(rpt);
                    }
                }
            }
        }

        profiler.endSection();
        return !myTreeSet.isEmpty();
    }

    @Inject("func_175712_a")
    public List<NextTickListEntry> getPendingBlockUpdates(StructureBoundingBox box, boolean remove) {
        List<NextTickListEntry> list = null;

        for(int i = 0; i < 2; i++) {
            List<NextTickListEntry> store = i == 0 ? myTreeSet : pendThisTick;

            for (int j = 0; j < store.size(); j++) {
                NextTickListEntry entry = store.get(j);
                BlockPos pos = entry.position;
                if (pos.getX() >= box.minX && pos.getX() < box.maxX && pos.getZ() >= box.minZ && pos.getZ() < box.maxZ) {
                    if (remove) {
                        if (i == 0) {
                            pendHashSet.remove(entry);
                        }

                        store.remove(j--);
                    }

                    if (list == null) list = Lists.newArrayList();

                    list.add(entry);
                }
            }
        }

        return list;
    }
}
