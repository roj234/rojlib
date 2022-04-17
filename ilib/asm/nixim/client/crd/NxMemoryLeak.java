package ilib.asm.nixim.client.crd;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import ilib.Config;
import ilib.asm.util.MCReplaces;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.VertexBufferUploader;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderWorker;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;

/**
 * @author Roj233
 * @since 2022/4/21 15:05
 */
@Nixim("net.minecraft.client.renderer.chunk.ChunkRenderDispatcher")
class NxMemoryLeak extends ChunkRenderDispatcher {
    @Shadow("field_178521_b")
    private static ThreadFactory THREAD_FACTORY;
    @Shadow("field_188249_c")
    private int countRenderBuilders;
    @Shadow("field_188250_d")
    private List<Thread> listWorkerThreads;
    @Shadow("field_178522_c")
    private List<ChunkRenderWorker> listThreadedWorkers;
    @Shadow("field_178519_d")
    private PriorityBlockingQueue<ChunkCompileTaskGenerator> queueChunkUpdates;
    @Shadow("field_178520_e")
    private BlockingQueue<RegionRenderCacheBuilder> queueFreeRenderBuilders;
    @Shadow("field_178517_f")
    private WorldVertexBufferUploader worldVertexUploader;
    @Shadow("field_178518_g")
    private VertexBufferUploader vertexUploader;
    @Shadow("field_178524_h")
    private Queue<?> queueChunkUploads;
    @Shadow("field_178525_i")
    private ChunkRenderWorker renderWorker;

    @Inject(value = "<init>", at = Inject.At.REPLACE)
    public NxMemoryLeak(int bufCnt) {
        this.listWorkerThreads = Lists.newArrayList();
        this.listThreadedWorkers = Lists.newArrayList();
        this.queueChunkUpdates = Queues.newPriorityBlockingQueue();
        this.worldVertexUploader = new WorldVertexBufferUploader();
        this.vertexUploader = new VertexBufferUploader();
        this.queueChunkUploads = Queues.newPriorityQueue();

        int thrCnt = Runtime.getRuntime().availableProcessors();
        if (bufCnt != 0) {
            int memCnt = Math.max(1, (int)(Runtime.getRuntime().maxMemory() * 0.3) / 10485760);
            bufCnt = bufCnt > 0 ? Math.min(bufCnt, thrCnt * Config.lowerChunkBuf) : thrCnt * Config.lowerChunkBuf;
            bufCnt = MathHelper.clamp(bufCnt, 1, memCnt);
            thrCnt = Math.min(bufCnt - 1, thrCnt);
        }

        this.countRenderBuilders = bufCnt;
        String name = "区块渲染器" + System.currentTimeMillis() % 1000 + " #";
        for(int i = 0; i < thrCnt; i++) {
            ChunkRenderWorker w = new ChunkRenderWorker(this);
            Thread thread = new MCReplaces.Worker(w);
            thread.setName(name+i);
            thread.setDaemon(true);
            thread.start();
            this.listThreadedWorkers.add(w);
            this.listWorkerThreads.add(thread);
        }
        this.queueFreeRenderBuilders = Queues.newArrayBlockingQueue(bufCnt);
        for(int i = 0; i < bufCnt; ++i) {
            this.queueFreeRenderBuilders.add(new RegionRenderCacheBuilder());
        }
        this.renderWorker = new ChunkRenderWorker(this, new RegionRenderCacheBuilder());
    }

    @Override
    @Inject("func_178507_a")
    public boolean updateChunkLater(RenderChunk rc) {
        rc.getLockCompileTask().lock();
        try {
            ChunkCompileTaskGenerator gen = rc.makeCompileTaskChunk();
            BlockPos pos = rc.getPosition().toImmutable();
            gen.addFinishRunnable(() -> {
                queueChunkUpdates.remove(gen);
            });
            queueChunkUpdates.offer(gen);
        } finally {
            rc.getLockCompileTask().unlock();
        }

        return true;
    }

    @Override
    @Inject("func_178509_c")
    public boolean updateTransparencyLater(RenderChunk rc) {
        rc.getLockCompileTask().lock();
        try {
            ChunkCompileTaskGenerator gen = rc.makeCompileTaskTransparency();
            if (gen != null) {
                BlockPos pos = rc.getPosition().toImmutable();
                gen.addFinishRunnable(() -> {
                    queueChunkUpdates.remove(gen);
                });
                queueChunkUpdates.offer(gen);
            }
        } finally {
            rc.getLockCompileTask().unlock();
        }

        return true;
    }
}
