package roj.plugins.minecraft;

import roj.archive.zip.EntryMod;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.asm.MemberDescriptor;
import roj.asmx.mapper.Mapper;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.concurrent.TaskPool;
import roj.config.ConfigMaster;
import roj.config.JsonSerializer;
import roj.config.Parser;
import roj.config.TextEmitter;
import roj.config.node.ConfigValue;
import roj.config.node.IntValue;
import roj.config.node.ListValue;
import roj.config.node.MapValue;
import roj.io.IOUtil;
import roj.math.Rect3d;
import roj.text.CharList;
import roj.text.ParseException;
import roj.text.TextReader;
import roj.text.TextWriter;
import roj.ui.Argument;
import roj.ui.Command;
import roj.ui.CommandNode;
import roj.ui.OptionParser;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2025/07/24 04:38
 */
public class DatapackHelper {
	private static List<ZipArchive> datapackList = new ArrayList<>();

	public static void main(String[] args) throws ParseException {
		CommandNode unmap = literal("dp_unmap").comment("fabric to forge").then(argument("文件夹", Argument.folder()).executes(ctx -> {
			var datax = new java.util.HashMap<String, String>();
			var m = new Mapper();
			m.loadMap(new File("D:\\work\\MCMake\\data\\map\\E-F-A-B.srg"), false);
			for (Map.Entry<String, String> entry : m.getClassMap().entrySet()) {
				datax.put(entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1), entry.getValue().substring(entry.getValue().lastIndexOf('/') + 1));
			}
			for (Map.Entry<MemberDescriptor, String> entry : m.getFieldMap().entrySet()) {
				datax.put(entry.getKey().name, entry.getValue());
			}
			for (Map.Entry<MemberDescriptor, String> entry : m.getMethodMap().entrySet()) {
				datax.put(entry.getKey().name, entry.getValue());
			}

			var pat = Pattern.compile("(?:class|method|field|comp)_\\d+");
			for (File listFile : IOUtil.listFiles(ctx.argument("文件夹", File.class))) {
				String s = IOUtil.readString(listFile);
				CharList sb = new CharList(s);
				int i = sb.preg_replace_callback(pat, matcher -> {
					String group = matcher.group();
					String obj = datax.get(group);
					return obj == null ? group : Objects.requireNonNull(obj, group);
				});
				try (var fos = new FileOutputStream(listFile)) {
					fos.write(IOUtil.encodeUTF8(sb));
				}
			}
		}));

		var adddir = literal("add_dir").comment("添加数据包文件夹").then(argument("文件夹", Argument.folder()).executes(ctx -> {
			File dir = ctx.argument("文件夹", File.class);
			for (File file : dir.listFiles()) {
				if (file.getName().endsWith(".zip") || file.getName().endsWith(".jar")) {
					try {
						datapackList.add(new ZipArchive(file));
						System.out.println("添加了 "+file.getName());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}));
		var addpack = literal("add_pack").comment("添加数据包").then(argument("文件", Argument.file()).executes(ctx -> {
			datapackList.add(new ZipArchive(ctx.argument("文件", File.class)));
		}));
		var save = literal("save").comment("保存对文件的修改").executes(ctx -> {
			for (ZipArchive zipArchive : datapackList) {
				for (ZEntry entry : zipArchive.entries()) {
					String name = entry.getName();
					if (name.equals("pack.png") || name.equals("icon.png")) {
						try {
							var image = ImageIO.read(zipArchive.getStream(entry));
							if (image.getWidth() > 256 || image.getHeight() > 256) {
								BufferedImage small = new BufferedImage(256, 256, BufferedImage.TYPE_USHORT_565_RGB);
								small.createGraphics().drawImage(image, 0, 0, 256, 256, 0, 0, image.getWidth(), image.getHeight(), null);
								ByteList output = new ByteList();
								ImageIO.write(small, "PNG", output);
								if (output.wIndex() * 1.7 < entry.getSize()) {
									System.out.println("file "+name+" in "+zipArchive+", original="+entry.getSize()+", now "+output.wIndex());
									zipArchive.put(name, output);
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					if (entry.isDirectory() || entry.getName().startsWith(".cache")) {
						zipArchive.put(entry.getName(), null);
					}
				}
				zipArchive.save();
			}
			System.out.println("已优化并保存");
		});
		var close = literal("close").comment("丢弃未保存的修改并关闭所有数据包").executes(ctx -> {
			for (ZipArchive zipArchive : datapackList) {
				zipArchive.close();
			}
			datapackList.clear();
		});

		Command searcher = ctx -> {
			Pattern regex = Pattern.compile(ctx.argument("查找", String.class), ctx.context.startsWith("find") ? Pattern.LITERAL : 0);
			var pool = TaskPool.common().newGroup();
			for (var zf : datapackList) {
				pool.executeUnsafe(() -> {
					for (ZEntry ze : zf.entries()) {
						if (ze.getName().endsWith(".json")) {
							try (TextReader in = new TextReader(zf.getStream(ze), StandardCharsets.UTF_8)) {
								CharList sb = IOUtil.getSharedCharBuf().readFully(in);
								sb.preg_match_callback(regex, m -> {
									System.out.println(zf.source().toString()+"!"+ze.getName()+"@"+m.start()+": "+m.group());
								});
							}
						}
					}
				});
			}
			pool.await();
		};

		Command replacer = ctx -> {
			Pattern regex = Pattern.compile(ctx.argument("查找", String.class), ctx.context.startsWith("replace") ? Pattern.LITERAL : 0);
			String replace = ctx.argument("替换", String.class);
			var pool = TaskPool.common().newGroup();
			for (var zf : datapackList) {
				pool.executeUnsafe(() -> {
					IntValue flag = new IntValue();
					for (ZEntry ze : zf.entries()) {
						if (ze.getName().endsWith(".json")) {
							try (TextReader in = new TextReader(zf.getStream(ze), StandardCharsets.UTF_8)) {
								flag.value = 0;
								CharList sb = IOUtil.getSharedCharBuf().readFully(in);
								sb.preg_replace_callback(regex, m -> {
									CharList tmp = new CharList(replace);
									for (int i = 0; i < m.groupCount(); i++)
										tmp.replace("$"+i, m.group(i));
									String str = tmp.toStringAndFree();

									if (!str.equals(m.group())) flag.value = 1;
									return str;
								});

								if (flag.value != 0) {
									ByteList byteList = new ByteList().putUTFData(sb);
									zf.put(ze.getName(), byteList).flag = EntryMod.COMPRESS;
								}
							}
						}
					}
				});
			}
			pool.await();
		};

		var find = literal("find").comment("查找字符串").then(argument("查找", Argument.string()).executes(searcher));
		var regfind = literal("regfind").comment("正则查找字符串").then(argument("查找", Argument.string()).executes(searcher));
		var replace = literal("replace").comment("替换字符串").then(argument("查找", Argument.string()).then(argument("替换", Argument.string()).executes(replacer)));
		var regreplace = literal("regreplace").comment("正则替换字符串").then(argument("查找", Argument.string()).then(argument("替换", Argument.string()).executes(replacer)));

		var remove_signature = literal("removesign").comment("清除JAR签名").executes(ctx -> {
			for (ZipArchive za : datapackList) {
				for (ZEntry entry : za.entries()) {
					if (entry.getName().startsWith("META-INF/") && entry.getName().lastIndexOf('/') == 8 && (entry.getName().endsWith(".SF") || entry.getName().endsWith(".RSA"))) {
						za.put(entry.getName(), null);
					}
				}
			}
		});

		var face_compress = literal("autocull").comment("自动方块状态剔除").executes(ctx -> {
			for (ZipArchive zipArchive : datapackList) {
				cullFace(zipArchive);
			}
		});

		var dump_missing_translation = literal("chinese").comment("导出缺失的中文翻译").then(argument("to", Argument.fileOptional(true)).executes(ctx -> {
			try (var out = TextWriter.to(ctx.argument("to", File.class))) {
				var json = new JsonSerializer("\t").to(out);
				json.emitMap();
				for (ZipArchive in : datapackList) {
					dumpZhcn(in, json);
				}
				json.pop();
				json.close();
			}
		}));

		var updatecheck = literal("updatecheck").comment("还有必要等它更新吗(1y)").executes(ctx -> {

		});

		new OptionParser("DatapackUtils").add(unmap).add(adddir).add(addpack)
				.add(save).add(close).add(find).add(regfind).add(replace).add(regreplace)
				.add(remove_signature).add(dump_missing_translation).add(face_compress)
				.parse(args, true);
	}

	private static final Pattern TRANSLATION_PATTERN = Pattern.compile("^assets/([a-z_\\-]+)/lang/(.._..)\\.json");
	record LangState(LinkedHashMap<String, String> translation, ZEntry hash) {}
	@SuppressWarnings("unchecked")
	private static void dumpZhcn(ZipArchive za, TextEmitter json) {
		HashMap<String, Map<String, LangState>> states = new HashMap<>();

		for (ZEntry ze : za.entries()) {
			Matcher matcher = TRANSLATION_PATTERN.matcher(ze.getName());
			if (matcher.matches()) {
				var modid = matcher.group(1);
				var langid = matcher.group(2);

				Map<String, LangState> langState = states.computeIfAbsent(modid, Helpers.fnHashMap());

				try {
					var translation = (LinkedHashMap<String, String>) ConfigMaster.JSON.parser().parse(za.getStream(ze), Parser.ORDERED_MAP).unwrap();

					LangState state = new LangState(translation, ze);
					if (langid.equals("en_us")) {
						for (Iterator<LangState> iterator = langState.values().iterator(); iterator.hasNext(); ) {
							LangState value = iterator.next();
							if (value.hash.getCrc32() == ze.getCrc32()) {
								System.out.println("删除和en_us.json内容相同的" + value.hash.getName());
								za.put(value.hash.getName(), null);
								iterator.remove();
							}
						}
					} else {
						LangState value = langState.get("en_us");
						if (value != null) {
							if (value.hash.getCrc32() == ze.getCrc32()) {
								System.out.println("删除和en_us.json内容相同的"+value.hash.getName());
								za.put(value.hash.getName(), null);
								continue;
							}
						}
					}
					langState.put(langid, state);
				} catch (IOException | ParseException e) {
					System.err.println("Failure processing "+ze.getName());
					e.printStackTrace();
				}
			}
		}

		for (var entry : states.entrySet()) {
			String modid = entry.getKey();
			LangState enUs = entry.getValue().get("en_us");
			if (enUs == null) {
				System.out.println("警告：模组"+modid+"不存在英文文本，跳过");
				continue;
			}
			LangState zhCn = entry.getValue().get("zh_cn");
			if (zhCn == null) {
				zhCn = new LangState(new LinkedHashMap<>(), null);
			}

			var copyOf = new LinkedHashMap<>(enUs.translation);
			for (Map.Entry<String, String> trans : zhCn.translation.entrySet()) {
				String englishValue = copyOf.get(trans.getKey());
				if (englishValue == null) {
					System.out.println("警告：模组"+modid+"的翻译条目\""+trans+"\"不存在英文文本");
					continue;
				}

				if (!englishValue.equals(trans.getValue()))
					copyOf.remove(trans.getKey());
			}

			if (copyOf.size() > 0) {
				System.out.println("正在创建"+modid+"的翻译");
				json.key("_");
				json.emit("模组ID="+modid+", 条目="+copyOf.size());
				for (Map.Entry<String, String> entry1 : copyOf.entrySet()) {
					json.key(entry1.getKey());
					json.emit(entry1.getValue());
				}
			}
		}
	}

	private static final Pattern BLOCK_MODEL_PATTERN = Pattern.compile("^assets/[a-z_\\-]+/models/block/.+\\.json");
	public static void cullFace(ZipArchive za) {
		var up_plane = new Rect3d(-1, 15.99, -1, 17, 17, 17);
		var down_plane = new Rect3d(-1, -1, -1, 17, 0.01, 17);
		var north_plane = new Rect3d(-1, -1, -1, 17, 17, 0.01);
		var south_plane = new Rect3d(-1, -1, 15.99, 17, 17, 17);
		var east_plane = new Rect3d(15.99, -1, -1, 17, 17, 17);
		var west_plane = new Rect3d(-1, -1, -1, 0.01, 17, 17);

		for (ZEntry ze : za.entries()) {
			if (BLOCK_MODEL_PATTERN.matcher(ze.getName()).matches()) {
				MapValue map = null;
				try {
					map = ConfigMaster.JSON.parse(za.getStream(ze)).asMap();

					for (ConfigValue entry : map.getList("elements")) {
						MapValue map1 = entry.asMap();
						if (map1.containsKey("rotation")) continue;
						ListValue from = map1.getList("from");
						ListValue to = map1.getList("to");

						var rect = new Rect3d(from.getDouble(0), from.getDouble(1), from.getDouble(2), to.getDouble(0), to.getDouble(1), to.getDouble(2));
						for (Map.Entry<String, ConfigValue> faces : map1.getMap("faces").entrySet()) {
							String faceKey = faces.getKey();
							boolean isCoincident = switch (faceKey) {
								case "up" -> rect.maxY >= up_plane.minY && rect.maxY <= up_plane.maxY;
								case "down" -> rect.minY >= down_plane.minY && rect.minY <= down_plane.maxY;
								case "north" -> rect.minZ >= north_plane.minZ && rect.minZ <= north_plane.maxZ;
								case "south" -> rect.maxZ >= south_plane.minZ && rect.maxZ <= south_plane.maxZ;
								case "west" -> rect.minX >= west_plane.minX && rect.minX <= west_plane.maxX;
								case "east" -> rect.maxX >= east_plane.minX && rect.maxX <= east_plane.maxX;
								default -> false;
							};

							if (isCoincident && faces.getValue().asMap().getString("cullface").isEmpty()) {
								System.out.println("Face "+rect+" cull="+faceKey);
								faces.getValue().asMap().put("cullface", faceKey);
							}
						}
					}

					ByteList buf = IOUtil.getSharedByteBuf();
					ConfigMaster.JSON.toBytes(map, buf);
					za.put(ze.getName(), new ByteList(buf.toByteArray()));
					System.out.println("Processed "+ze.getName());
				} catch (IOException | ParseException e) {
					System.err.println("Failure processing "+ze.getName());
					e.printStackTrace();
				}

			}
		}
	}
}
