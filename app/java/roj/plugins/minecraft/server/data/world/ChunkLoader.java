package roj.plugins.minecraft.server.data.world;

import org.jetbrains.annotations.NotNull;
import roj.collect.HashSet;
import roj.collect.LRUCache;
import roj.concurrent.Promise;
import roj.concurrent.TimerTask;
import roj.concurrent.Timer;
import roj.config.data.CInt;
import roj.io.RegionFile;
import roj.io.source.FileSource;
import roj.text.logging.Logger;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/3/20 5:40
 */
public final class ChunkLoader implements Closeable {
	private static final Logger LOGGER = Logger.getLogger();

	private final File chunkFolder;
	private final LRUCache<CInt, RegionFile> chunks = new LRUCache<>(50);

	private CInt _id = new CInt();

	private final HashSet<Chunk> dirtyChunks = new HashSet<>();
	private TimerTask asyncSave;

	public ChunkLoader(File folder) {
		chunkFolder = folder;
		chunks.setEvictListener((key, file) -> {
			try {
				file.close();
			} catch (IOException e) {
				LOGGER.error("regionFile close error", e);
			}
		});
	}

	@NotNull
	private synchronized RegionFile getRegion(int x, int z) throws IOException {
		CInt _id = this._id;
		if (_id == null) _id = new CInt();
		else this._id = null;

		int x1 = x /32, z1 = z /32;
		int id = x1 << 16 | z1;
		_id.value = id;

		RegionFile region = chunks.get(_id);
		if (region == null) {
			region = new RegionFile(new FileSource(new File(chunkFolder, "r"+x1+"."+z1+".mcx")), 1024, 1024, RegionFile.F_USE_NEW_RULE);
			chunks.put(new CInt(id), region);
		}

		this._id = _id;
		return region;
	}

	public Chunk loadChunk(int x, int z) {
		try {
			return loadChunkExceptional(x, z);
		} catch (Exception e) {
			LOGGER.error("chunk load error", e);
			return null;
		}
	}
	public Promise<Chunk> loadChunkAsync(int x, int z) {return Promise.callAsync(() -> loadChunkExceptional(x, z));}
	private Chunk loadChunkExceptional(int x, int z) throws IOException {
		RegionFile region = getRegion(x, z);
		int id = ((x & 31) << 5) | (z & 31);
		if (!region.hasData(id)) return null;

		Chunk chunk = new Chunk(x, z);
		chunk.loadData(region.getInputStream(id, null));
		return chunk;
	}

	public void markChunkDirty(int x, int z, Chunk chunk) {
		synchronized (this) {
			dirtyChunks.add(chunk);
			if (asyncSave == null) {
				asyncSave = Timer.getDefault().delay(this::saveChunks, 1000);
			}
		}
	}

	public void saveChunks() {
		Object[] array;
		synchronized (this) {
			array = dirtyChunks.toArray();
			dirtyChunks.clear();
			asyncSave = null;
		}
		for (int i = 0; i < array.length; i++) {
			Chunk chunk = (Chunk) array[i];

			int x = chunk.x, z = chunk.z;

			try {
				var out = getRegion(x, z).getOutputStream(((x & 31) << 5) | (z & 31), RegionFile.DEFLATE);
				assert out != null;
				try {
					chunk.write(out);
				} catch (Exception e) {
					out.cancel();
					throw e;
				}
				out.close();
			} catch (IOException e) {
				LOGGER.error("无法保存区块", e);
			}
		}
	}

	@Override
	public void close() throws IOException {
		TimerTask task = asyncSave;
		if (task != null) task.cancel();

		saveChunks();
		for (RegionFile file : chunks.values()) {
			try {
				file.close();
			} catch (IOException e) {
				LOGGER.error("regionFile close error", e);
			}
		}
	}
}