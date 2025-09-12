package roj.plugins.minecraft.diff;

import roj.collect.HashSet;
import roj.collect.LongMap;
import roj.concurrent.TaskGroup;
import roj.config.ConfigMaster;
import roj.config.node.ConfigValue;
import roj.config.node.ListValue;
import roj.config.node.Type;
import roj.io.IOUtil;
import roj.io.RegionFile;
import roj.renderer.util.Stitcher;
import roj.renderer.util.Tile;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static roj.plugins.minecraft.diff.ChunkTrim.*;

/**
 * @author Roj234
 * @since 2024/8/15 20:34
 */
public class GroupBy {
	public static void createBackup(File from, File to) {
		File[] chunks = new File(from, "region").listFiles();
		var data = generateRemap(chunks, pool.newGroup());

		File regions = new File(to, "region");regions.mkdir();
		applyRemap(data, new File(from, "region"), regions, pool.newGroup());
		File entities = new File(to, "entities");entities.mkdir();
		applyRemap(data, new File(from, "entities"), entities, pool.newGroup());
		File poi = new File(to, "poi");poi.mkdir();
		applyRemap(data, new File(from, "poi"), poi, pool.newGroup());
	}

	private static LongMap<Long> generateRemap(File[] fileList, TaskGroup asyncPool) {
		bar.setTotal(fileList.length * 1024L);
		Set<Long> chunks = Collections.newSetFromMap(new ConcurrentHashMap<>());

		for (File mca : fileList) {
			var match = REGION_MATCHER.matcher(mca.getName());
			if (!match.find()) {
				bar.increment(1024);
				continue;
			}

			int fileX = Integer.parseInt(match.group(1)) * 32;
			int fileZ = Integer.parseInt(match.group(2)) * 32;
			asyncPool.execute(() -> {
				try (var rf = new RegionFile(mca)) {
					for (int j = 0; j < 1024; j++) {
						bar.increment(1);

						if (!rf.hasData(j)) continue;

						int x = fileX + (j & 31);
						int z = fileZ + j / 32;
						bar.setName(x + "," + z);

						_ChunkPos chunk;
						try (var nbt = rf.getBufferedInputStream(j)) {
							chunk = ConfigMaster.NBT.readObject(_ChunkPos.class, nbt);
							chunks.add(chunkPos(chunk.xPos, chunk.zPos));
						} catch (Exception e) {
						}
					}
				} catch (IOException e) {
					System.out.println("无法锁定文件:" + e.getMessage());
				}
			});
		}
		asyncPool.await();

		bar.end();

		var stitcher = new Stitcher(65536, 65536, 0, 0);
		var bfs = new HashSet<Long>();
		var bfsNext = new HashSet<Long>();

		System.out.println("Grouping "+chunks.size()+" chunks");
		while(!chunks.isEmpty()) {
			Iterator<Long> iterator = chunks.iterator();
			bfs.add(iterator.next());
			iterator.remove();

			var group = new ChunkGroup();
			do {
				group.addAll(bfs);
				for (long pos : bfs) {
					int x = (int) (pos >>> 32);
					int z = (int) pos;
					long o;

					o = chunkPos(x + 1, z);
					if (chunks.remove(o)) bfsNext.add(o);
					o = chunkPos(x - 1, z);
					if (chunks.remove(o)) bfsNext.add(o);
					o = chunkPos(x, z + 1);
					if (chunks.remove(o)) bfsNext.add(o);
					o = chunkPos(x, z - 1);
					if (chunks.remove(o)) bfsNext.add(o);
				}

				var tmp = bfs;
				bfs = bfsNext;
				bfsNext = tmp;
				bfsNext.clear();

			} while (!bfs.isEmpty());

			if (group.chunks.size() > 1)
				stitcher.add(group);
		}
		System.out.println("Tiling "+stitcher.getTiles().size()+" groups");

		stitcher.stitch();

		LongMap<Long> chunkRemap = new LongMap<>();
		List<Tile> tiles = stitcher.getTiles();
		System.out.println("Map to "+stitcher.getWidth()+"x"+stitcher.getHeight()+" atlas");
		for (int i = 0; i < tiles.size(); i++) {
			var group = (ChunkGroup)tiles.get(i);

			for (long pos : group.chunks) {
				int x = (int) (pos >>> 32);
				int z = (int) pos;
				// 蕨了，好怪的javac的bug，完全搞不懂呢，这里不是很好判断优先级吗？
				chunkRemap.put(pos, (Long) chunkPos(x + group.offsetX, z + group.offsetZ));
			}
		}

		System.out.println("RemapTable size="+chunkRemap.size());
		return chunkRemap;
	}
	private static void applyRemap(LongMap<Long> remap, File fromPath, File toPath, TaskGroup asyncPool) {
		var map = new ConcurrentHashMap<Integer, RegionFile>();
		Function<Integer, RegionFile> open = integer -> {
			short x = (short) (integer >>> 16);
			short z = integer.shortValue();
			var fileName = new File(toPath, "r." + x + "." + z + ".mca");
			try {
				return new RegionFile(fileName);
			} catch (IOException e) {
				return Helpers.athrow2(e);
			}
		};

		File[] fileList = fromPath.listFiles();

		bar.setName("复制区块");
		bar.setTotal(fileList.length * 1024L);

		for (var mca : fileList) {
			var match = REGION_MATCHER.matcher(mca.getName());
			if (!match.find()) continue;

			int fileX = Integer.parseInt(match.group(1)) * 32;
			int fileZ = Integer.parseInt(match.group(2)) * 32;

			asyncPool.execute(() -> {
				try (var rf = new RegionFile(mca)) {
					for (int i = 0; i < 1024; i++) {
						if (rf.hasData(i)) try (var nbt = rf.getBufferedInputStream(i)) {
							var data = ConfigMaster.NBT.parse(nbt).asMap();
							long chunkPos;
							if (data.containsKey("xPos")) {
								chunkPos = chunkPos(data.getInt("xPos"), data.getInt("zPos"));
							} else if (data.containsKey("Position", Type.LIST)) {
								ListValue list = data.getList("Position");
								chunkPos = chunkPos(list.getInteger(0), list.getInteger(1));
							} else {
								System.out.println(data);
								chunkPos = chunkPos(fileX + (i & 31), fileZ + (i >> 5));
							}

							var remapPos = remap.get(chunkPos);
							if (remapPos == null) continue;

							int mcaPos = chunkToMca(remapPos);

							boolean changed = false;
							if (remapPos != chunkPos) {
								if (data.containsKey("xPos")) {
									data.put("xPos", (int) (remapPos >>> 32));
									data.put("zPos", remapPos.intValue());
									changed = true;
								} else if (data.containsKey("Position", Type.LIST)) {
									ListValue list = data.getList("Position");
									list.set(0, ConfigValue.valueOf((int) (remapPos >>> 32)));
									list.set(1, ConfigValue.valueOf(remapPos.intValue()));
									changed = true;
								}
							}

							RegionFile file = map.computeIfAbsent(mcaPos, open);
							if (changed) {
								ConfigMaster.NBT.toBytes(data, file.getOutputStream(chunkToMcaSub(remapPos), RegionFile.DEFLATE));
							} else {
								var in = rf.getRawdata(i, null);
								if (in != null) {
									ByteList tmp = IOUtil.getSharedByteBuf();
									tmp.readStreamFully(in);
									file.write(chunkToMcaSub(remapPos), tmp);
								}
							}

						} catch (Exception e) {
							e.printStackTrace();
						}
						bar.increment(1);
					}
				} catch (IOException e) {
					System.out.println("无法锁定文件:"+e.getMessage());
				}
			});
		}
		asyncPool.await();
		bar.end("Remap ok");
		for (RegionFile value : map.values()) {
			IOUtil.closeSilently(value);
		}
	}

	private static int chunkToMcaSub(long pos) {
		int x = (int) (pos >>> 32);
		int z = (int) pos;
		return ((z & 31) << 5) | (x & 31);
	}

	private static int chunkToMca(long pos) {
		int x = (int) (pos >>> 32);
		int z = (int) pos;
		return ((x/32) << 16) | ((z/32)&0xFFFF);
	}

	private static long chunkPos(int x, int z) {return ((long)x << 32) | (z&0xFFFFFFFFL);}

	private static final class _ChunkPos { int xPos, zPos;}
}
