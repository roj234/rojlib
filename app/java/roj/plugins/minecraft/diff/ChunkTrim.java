package roj.plugins.minecraft.diff;

import roj.collect.*;
import roj.concurrent.TaskGroup;
import roj.concurrent.TaskPool;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.auto.Optional;
import roj.io.IOUtil;
import roj.io.RegionFile;
import roj.ui.*;
import roj.util.ArrayCache;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2024/8/15 20:34
 */
public class ChunkTrim {
	static final Pattern REGION_MATCHER = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");
	static EasyProgressBar bar = new EasyProgressBar("计算", "区块");
	static TaskPool pool = TaskPool.common();

	private static MCTWhitelist whitelist;

	public static void init(File file1) throws IOException, ParseException {
		whitelist = ConfigMaster.JSON.readObject(MCTWhitelist.class, file1);
	}

	public static void createBackup(File worldDir, File backup) {
		File[] chunks = new File(worldDir, "region").listFiles();
		File regions = new File(backup, "region");regions.mkdir();
		RegionFile[] data = removeBlockChunk(chunks, whitelist, regions, pool.newGroup());
		if (data == null) return;
		File entities = new File(backup, "entities");entities.mkdir();
		removeOtherChunk(data, new File(worldDir, "entities"), entities, pool.newGroup());
		File poi = new File(backup, "poi");poi.mkdir();
		removeOtherChunk(data, new File(worldDir, "poi"), poi, pool.newGroup());
	}
	public static void createInline(File worldDir) {
		File[] chunks = new File(worldDir, "region").listFiles();
		RegionFile[] data = removeBlockChunk(chunks, whitelist, null, pool.newGroup());
		if (data == null) return;
		removeOtherChunk(data, new File(worldDir, "entities"), null, pool.newGroup());
		removeOtherChunk(data, new File(worldDir, "poi"), null, pool.newGroup());
	}

	private static RegionFile[] removeBlockChunk(File[] fileList, MCTWhitelist whitelist, File trimToPath, TaskGroup asyncPool) {
		var toClear = new HashMap<String,IntList>();

		RegionFile[] definedState = new RegionFile[fileList.length];
		bar.setTotal(fileList.length * 1024L);

		for (int i = 0; i < fileList.length; i++) {
			File mca = fileList[i];

			var match = REGION_MATCHER.matcher(mca.getName());
			if (!match.find()) {bar.increment(1024);continue;}

			int fileX = Integer.parseInt(match.group(1)) * 32;
			int fileZ = Integer.parseInt(match.group(2)) * 32;
			int javac傻逼 = i;
			asyncPool.execute(() -> {
				HashSet<String> whitelistTest = new HashSet<>();
				try (var rf = new RegionFile(mca)) {
					definedState[javac傻逼] = rf;
					for (int j = 0; j < 1024; j++) {
						bar.increment(1);

						if (!rf.hasData(j)) continue;

						String delete;
						int x = fileX+(j & 31);
						int z = fileZ+j / 32;
						bar.setName(x+","+z);

						delete:{
							int timestamp = rf.getTimestamp(j);
							//if (currentTime - timestamp > 86400 * 31) {delete = "超过31天未访问";break delete;}

							NbtChunk chunk;
							try (var nbt = rf.getBufferedInputStream(j)) {
								chunk = ConfigMaster.NBT.readObject(NbtChunk.class, nbt);
							} catch (Exception e) {
								e.printStackTrace();
								delete = "数据错误(Data Error)";
								break delete;
							}

							if (chunk.xPos != x || chunk.zPos != z) {delete = "数据错误(Pos Error)";break delete;}
							if (!chunk.Status.equals("minecraft:full") && !chunk.Status.equals("full")) {delete = "未生成完全("+chunk.Status+")";break delete;}
							if (chunk.InhabitedTime == 0) {delete = "玩家从未踏足";break delete;}
							if (chunk.InhabitedTime < 20 * 60) {delete = "存在玩家的时间<1分钟";break delete;}

							int allDim = 10;
							for (Section section : chunk.sections) {
								if (section.block_states != null) {
									if (section.biomes != null) {
										int dim = 0;
										for (String s : section.biomes.palette) {
											switch (s) {
												case "minecraft:soul_sand_valley", "minecraft:nether_wastes", "minecraft:crimson_forest", "minecraft:basalt_deltas" -> {
													dim = -1;//NETHER
												}
												case "minecraft:end_highlands", "minecraft:end_midlands", "minecraft:end_barrens", "minecraft:small_end_islands" -> {
													dim = 1;//THE_END
												}
												default -> {
													dim = 0;
												}
											}
										}
										if (allDim == 10) {
											allDim = dim;
										} else if (allDim != dim) {
											allDim = 11;
										}
									}
								}
							}

							if (allDim >= 11) {
								System.out.println("警告：区块"+x+","+z+"无法确定世界（根据生物群系），按主世界计算");
								allDim = 0;
							}
							whitelistTest.clear();
							whitelistTest.addAll(switch (allDim) {
								case -1 -> whitelist.nether;
								default -> whitelist.world;
								case 1 -> whitelist.the_end;
							});

							for (Section section : chunk.sections) {
								if (section.block_states != null) {
									for (Block block : section.block_states.palette) {
										if (!whitelistTest.contains(block.Name)) {
											delete = "保留";
											break delete;
										}
									}
								}
							}

							delete = "放置的方块全部在忽略名单中";
						}

						synchronized (toClear) {
							IntList r = toClear.computeIfAbsent(delete, n -> new IntList());
							r.add(x);
							r.add(z);
						}
					}
				} catch (IOException e) {
					System.out.println("无法锁定文件:"+e.getMessage());
				}
			});
		}
		asyncPool.await();

		bar.end();
		for (var entry : toClear.entrySet()) {
			System.out.println("类型: "+entry.getKey()+", 数量: "+entry.getValue().size()/2);
		}
		toClear.remove("保留");

		char yn;
		if (trimToPath == null) {
			yn = TUI.key("YyNn", "是否需要立即清除？(Yn)");
			if (yn != 'y' && yn != 'Y') return null;

			yn = TUI.key("YyNn", "是否需要压缩区块？(Yn)");
			if (yn == 0) return definedState;
		} else {
			yn = 'y';
		}

		var c = new Shell("\u001b[;97m输入需要删除的区块类型,Ctrl+C以结束 > ");
		c.setInputEcho(true);

		LongMap<BitSet> deleteChunk = new LongMap<>();
		int max = 0;
		while (true) {
			var chunks = c.readSync(Argument.oneOf(toClear));
			if (chunks == null) break;
			toClear.values().remove(chunks);

			max += chunks.size();
			for (int i = 0; i < chunks.size(); i += 2) {
				int x = chunks.get(i), z = chunks.get(i+1);

				long mcaKey = ((long) (x >> 5) << 32) | ((z >> 5) & 0xFFFFFFFFL);
				deleteChunk.computeIfAbsent(mcaKey, s -> new BitSet()).add((x&31) | ((z&31) << 5));
			}
		}

		bar.setName("删除区块");
		bar.setTotal(max);

		for (int i = 0; i < fileList.length; i++) {
			File mca = fileList[i];
			var match = REGION_MATCHER.matcher(mca.getName());
			if (!match.matches()) continue;

			int fileX = Integer.parseInt(match.group(1));
			int fileZ = Integer.parseInt(match.group(2));

			var toDelete = deleteChunk.get(((long) fileX << 32) | (fileZ & 0xFFFFFFFFL));
			if (toDelete == null) {
				if (trimToPath != null) {
					try {
						IOUtil.copyFile(mca, new File(trimToPath, mca.getName()));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				continue;
			}

			int javac大傻逼 = i;
			boolean isCompress = yn == 'y' || yn == 'Y';
			asyncPool.execute(() -> {
				File file1 = null;

				try (var rf = new RegionFile(mca)) {
					if (isCompress) {
						file1 = trimToPath == null ? new File(mca+".tmp") : new File(trimToPath, mca.getName());
						RegionFile newChunk = null;
						try {
							for (int j = 0; j < 1024; j++) {
								if (rf.hasData(j) && !toDelete.contains(j)) {
									if (newChunk == null) newChunk = new RegionFile(file1);
									rf.copyOneTo(newChunk, j);
								}
							}
							definedState[javac大傻逼] = newChunk;
						} finally {
							if (newChunk != null) IOUtil.closeSilently(newChunk);
							else file1 = null;
						}
					} else {
						for (var itr = toDelete.iterator(); itr.hasNext(); ) {
							rf.delete(itr.nextInt());
						}
					}
				} catch (IOException e) {
					System.out.println("无法锁定文件:"+e.getMessage());
				}

				if (isCompress && trimToPath == null) {
					if (!mca.delete()) {
						System.out.println("警告，文件"+mca+"被锁定，删除失败");
						if (file1 != null) file1.delete();
					} else if (file1 != null && !file1.renameTo(mca)) {
						System.out.println("警告，致命错误，区块数据"+mca+"已丢失");
					}
				}
			});
		}
		asyncPool.await();
		bar.end(max+"个区块已删除");
		return definedState;
	}
	private static void removeOtherChunk(RegionFile[] fileList, File basePath, File trimToPath, TaskGroup asyncPool) {
		char yn = trimToPath == null ? TUI.key("YyNn", "是否需要压缩区块？(Yn)") : 'y';

		bar.setName("删除区块");
		bar.setTotal(fileList.length * 1024L);

		var del = new AtomicInteger();
		boolean isCompress = yn == 'y' || yn == 'Y';
		for (var prev : fileList) {
			if (prev == null) {bar.addTotal(1024);continue;}

			var match = REGION_MATCHER.matcher(prev.file);
			if (!match.find()) continue;

			var mca = new File(basePath, "r."+match.group(1)+"."+match.group(2)+".mca");
			if (!mca.isFile()) {bar.addTotal(1024);continue;}

			asyncPool.execute(() -> {
				File file1 = null;
				try (var rf = new RegionFile(mca)) {
					if (isCompress) {
						file1 = trimToPath == null ? new File(mca+".tmp1") : new File(trimToPath, mca.getName());
						RegionFile newChunk = null;
						try {
							for (int i = 0; i < 1024; i++) {
								if (prev.hasData(i)) {
									if (newChunk == null) newChunk = new RegionFile(file1);
									rf.copyOneTo(newChunk, i);
								} else if (rf.hasData(i)) del.getAndAdd(1);
							}
						} finally {
							if (newChunk != null) IOUtil.closeSilently(newChunk);
							else file1 = null;
						}
					} else {
						for (int i = 0; i < 1024; i++) {
							bar.increment(1);
							if (!prev.hasData(i)) {
								if (rf.hasData(i)) {
									del.getAndIncrement();
									rf.delete(i);
								}
							}
						}
					}
				} catch (IOException e) {
					System.out.println("无法锁定文件:"+e.getMessage());
				}

				if (isCompress && trimToPath == null) {
					if (!mca.delete()) {
						System.out.println("警告，文件"+mca+"被锁定，删除失败");
						if (file1 != null) file1.delete();
					} else if (file1 != null && !file1.renameTo(mca)) {
						System.out.println("警告，致命错误，区块数据"+mca+"已丢失");
					}
				}
			});
		}
		asyncPool.await();
		bar.end(del+"个区块已删除");
	}

	private static final class MCTWhitelist {
		final HashSet<String> world = new HashSet<>(), nether = new HashSet<>(), the_end = new HashSet<>();
	}

	private static final class NbtChunk {
		int LastUpdate, InhabitedTime, DataVersion;
		String Status;
		int xPos, yPos, zPos;
		List<Section> sections;
		@Override public String toString() {return "MinecraftChunk{"+"LastUpdate="+LastUpdate+", InhabitedTime="+InhabitedTime+", DataVersion="+DataVersion+", Status='"+Status+'\''+", xPos="+xPos+", yPos="+yPos+", zPos="+zPos+", sections="+sections+'}';}
	}
	private static final class Section {
		int Y;
		@Optional BlockContainer block_states;
		@Optional BiomeContainer biomes;
		@Override public String toString() {return "Section{"+"Y="+Y+", block_states="+block_states+'}';}
	}
	private static final class BlockContainer {
		List<Block> palette;
		@Optional long[] data = ArrayCache.LONGS;
		@Override public String toString() {return "BlockContainer{"+"palette="+palette+", data="+data.length+'}';}
	}
	private static final class BiomeContainer {
		List<String> palette;
		@Optional long[] data = ArrayCache.LONGS;
		@Override public String toString() {return "BiomeContainer{"+"palette="+palette+", data="+data.length+'}';}
	}
	private static final class Block {
		String Name;
		@Optional Map<String, String> Properties = Collections.emptyMap();
		@Override public String toString() {return "Block{"+"Name='"+Name+'\''+", Properties="+Properties+'}';}
	}
}
