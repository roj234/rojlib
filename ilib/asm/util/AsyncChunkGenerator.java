package ilib.asm.util;

import ilib.Config;
import ilib.ImpLib;
import ilib.asm.Loader;
import roj.collect.MyHashMap;
import roj.concurrent.TaskExecutor;
import roj.concurrent.task.AsyncTask;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

/**
 * @author solo6975
 * @since 2022/5/22 1:05
 */
public class AsyncChunkGenerator {
	static final Map<ChunkPos, AsyncTask<Chunk>> pendingTask;
	static final TaskExecutor e;

	static {
		if (Config.shouldAsyncWorldTick) {
			pendingTask = new ConcurrentHashMap<>();
		} else {
			pendingTask = new MyHashMap<>();
		}

		e = new TaskExecutor();
		e.setDaemon(true);
		e.start();
		ImpLib.EVENT_BUS.add("ServerStop", () -> {
			e.clearTasks();
			pendingTask.clear();
		});
	}

	public static AsyncTask<Chunk> createTask(ChunkPos pos, ChunkProviderServer cp) {
		AsyncTask<Chunk> task = pendingTask.get(pos);
		if (task == null) {
			pendingTask.put(pos, task = new AsyncTask<Chunk>() {
				@Override
				protected Chunk invoke() throws Exception {
					Chunk c;
					try {
						c = cp.chunkGenerator.generateChunk(pos.x, pos.z);
					} catch (Throwable e) {
						getException(CrashReport.makeCrashReport(e, "异步生成区块"), pos, cp);
						return null;
					}
					try {
						if (Config.asyncPopulate) {
							c.populate(cp, cp.chunkGenerator);
						}
					} catch (Throwable e) {
						getException(CrashReport.makeCrashReport(e, "异步装饰区块"), pos, cp);
						return null;
					}
					return c;
				}

				private void getException(CrashReport rpt, ChunkPos pos, ChunkProviderServer cp) {
					CrashReportCategory cat = rpt.makeCategory("目的区块");
					cat.addCrashSection("区块位置", String.format("%d,%d", pos.x, pos.z));
					cat.addCrashSection("生成器", cp.chunkGenerator);
					throw new ReportedException(rpt);
				}
			});
			e.pushTask(task);
		}
		return task;
	}

	public static void cancelTask(ChunkPos pos) {
		AsyncTask<Chunk> task1 = pendingTask.remove(pos);
		e.removeTask(task1);
	}

	public static void sync(long timeout) {
		e.pushTask(AsyncTask.fromVoid(() -> {
			long time;
			while ((time = timeout - System.nanoTime()) > 0) {
				LockSupport.parkNanos(time);
			}
		}));
	}

	public static Chunk syncCallback(AsyncTask<Chunk> task, ChunkPos pos, ChunkProviderServer cp) {
		AsyncTask<Chunk> task1 = pendingTask.remove(pos);
		if (task1 != task) {
			Loader.logger.warn("Already generated? at {}, t1={}, t2={}", pos, task1, task);
			try {
				if (task.isCancelled()) return null;
				return task.get();
			} catch (Exception e) {
				return null;
			}
		}

		Chunk c;
		try {
			if (task.isCancelled()) return null;
			c = task.get();
		} catch (Exception e) {
			Loader.logger.warn("Failed to AsyncGen", e.getCause());
			return null;
		}

		Chunk prev = cp.loadedChunks.put(ChunkPos.asLong(pos.x, pos.z), c);
		if (prev != null) Loader.logger.warn("Already generated? at {} {} {}", pos, c, prev);
		c.onLoad();
		if (!Config.asyncPopulate) c.populate(cp, cp.chunkGenerator);
		return c;
	}
}
