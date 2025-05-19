package roj.plugins.ci.minecraft;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asmx.Context;
import roj.asmx.TransformUtil;
import roj.asmx.mapper.Mapper;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.crypt.CRC32;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.MemorySource;
import roj.plugins.ci.Workspace;
import roj.ui.EasyProgressBar;
import roj.ui.Terminal;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static roj.plugins.ci.FMD.DATA_PATH;
import static roj.plugins.ci.FMD.LOGGER;

/**
 * @author Roj234
 * @since 2025/2/12 3:47
 */
public abstract sealed class MinecraftWorkspace {
	@Nullable
	public static Workspace build(CMap config) {
		File mcRoot = new File(config.getString("MC路径"));
		if (!mcRoot.isDirectory()) mcRoot = Terminal.readFile("MC目录(.minecraft)");
		if (!new File(mcRoot, "versions").isDirectory()) mcRoot = new File(mcRoot, ".minecraft");

		File versionPath = new File(mcRoot, "versions");
		var versions = MinecraftClientInfo.listVersions(versionPath);

		String mcJson;
		if (versions.isEmpty()) {
			Terminal.error("没有找到任何MC版本！请确认目录是否正确");
			return null;
		} else {
			mcJson = config.getString("MC版本");
			if (!versions.contains(mcJson))
				mcJson = versions.get(Terminal.readChosenFile(versions, "MC版本"));
		}

		var clientInfo = new MinecraftClientInfo();
		clientInfo.libraryPath = new File(mcRoot, "libraries");
		try {
			clientInfo.resolve(versionPath, mcJson);
			try (var bar = new EasyProgressBar("构建工作空间")) {
				MinecraftWorkspace instance = findProperInstance(clientInfo);
				if (instance == null) return null;
				bar.setName("初始化"+instance.getClass().getSimpleName()+"工作空间");
				return instance.init(bar, clientInfo);
			}
		} catch (IOException | ParseException e) {
			e.printStackTrace();
			return null;
		}
	}

	abstract Workspace init(EasyProgressBar bar, MinecraftClientInfo clientInfo) throws IOException;

	private static MinecraftWorkspace findProperInstance(MinecraftClientInfo info) {
		if (info.libraries.containsKey("cpw/mods:modlauncher")) {
			LOGGER.debug("模式：高版本Forge");
			return new Forge2();
		} else if (info.libraries.containsKey("net/minecraftforge:forge")) {
			LOGGER.debug("模式：低版本Forge");
			return null;
		} else if (false) {
			LOGGER.debug("模式：Fabric");
			return null;
		} else {
			LOGGER.debug("模式：未知");
			return null;
		}
	}
	private static final class Forge2 extends MinecraftWorkspace {
		byte[] atBytes;

		@Override
		@SuppressWarnings("unchecked")
		Workspace init(EasyProgressBar bar, MinecraftClientInfo clientInfo) throws IOException {
			bar.addTotal(6);

			bar.setName("收集信息");
			bar.increment();

			File com2int = Terminal.readFile("Srg/XSrg格式的【编译期到运行期】名称映射表（必须）");
			if (com2int == null) return null;
			File serverPath = Terminal.readFile("同版本的Forge服务端（可选，用于服务端开发）");

			var tmpSource = new MemorySource();
			var resources = new ZipFileWriter(tmpSource, 8);
			var clientData = new SimpleList<Context>();

			bar.setName("客户端");
			bar.increment();

			var clientForgeVersion = combine(clientInfo.libraryPath, clientInfo.gameArguments, clientData, resources, false);
			if (clientForgeVersion == null) return null;

			bar.setName("依赖");
			bar.increment();

			String mcVersion = clientForgeVersion.substring(0, clientForgeVersion.indexOf('-'));
			String forgeVersion = clientForgeVersion.substring(clientForgeVersion.indexOf('-')+1);

			for (var folder : new File(clientInfo.libraryPath, "net/minecraftforge").listFiles()) {
				var mavenNoClassifiers = new File(folder, mcVersion+"-"+forgeVersion+"/"+folder.getName()+"-"+mcVersion+"-"+forgeVersion+".jar");
				if (mavenNoClassifiers.isFile()) {
					MinecraftClientInfo.Library value = new MinecraftClientInfo.Library("net/minecraftforge:"+folder.getName());
					value.path = mavenNoClassifiers.getAbsolutePath().substring(clientInfo.libraryPath.getAbsolutePath().length()+1);
					clientInfo.libraries.add(value);
				}
			}

			File libraries = new File(DATA_PATH, ".MC"+mcVersion+"FG"+forgeVersion+"_lib.jar");
			combineLibrary(clientInfo, libraries);

			bar.setName("服务端");
			bar.increment();

			String serverSrgCombined = null;
			if (serverPath != null) {
				var argumentFile = new File(serverPath, "libraries/net/minecraftforge/forge/"+clientForgeVersion+"/win_args.txt");
				if (!argumentFile.isFile()) {
					Terminal.warning("服务端加载失败：找不到这个文件："+argumentFile);
					Terminal.warning("请确认服务端和客户端是相同版本"+clientForgeVersion+"且能运行");
					return null;
				}

				var text = IOUtil.readString(argumentFile);
				var arguments = MinecraftClientInfo.tokenize(text);

				var serverData = new SimpleList<Context>();
				serverSrgCombined = combine(new File(serverPath, "libraries"), arguments, serverData, resources, true);
				if (serverSrgCombined == null) return null;

				var merger = new ClassMerger();
				clientData = new SimpleList<>(merger.process(clientData, serverData));
				LOGGER.debug("ClassMerger: {}/{} entries, {} combined", merger.clientOnly, merger.both, merger.mergedField+merger.mergedMethod+merger.replaceMethod);
			}

			for (int i = 0; i < clientData.size(); i++) {
				var ctx = clientData.get(i);
				resources.writeNamed(ctx.getFileName(), ctx.getCompressedShared());
			}
			resources.close();
			var combinedCache = new File(DATA_PATH, ".MC"+mcVersion+"FG"+forgeVersion+(serverSrgCombined!=null?"+S":"")+"_srg.jar");
			try (var fos = new FileOutputStream(combinedCache)) {
				tmpSource.buffer().writeToStream(fos);
			}

			bar.setName("映射");
			bar.increment();

			var mapper = new Mapper();
			mapper.loadMap(com2int, true);
			mapper.loadLibraries(Arrays.asList(libraries, combinedCache));
			mapper.packup();
			var atList = AT.buildATMapFromATCfg(new ByteArrayInputStream(atBytes), mapper);

			var combinedCacheMcp = new File(DATA_PATH, ".MC"+mcVersion+"FG"+forgeVersion+(serverSrgCombined!=null?"+S":"")+"_mcp.jar");
			try (var zfw2 = new ZipFileWriter(combinedCacheMcp)) {
				mapper.map(clientData);

				for (int i = 0; i < clientData.size(); i++) {
					var ctx = clientData.get(i);

					var at = atList.remove(ctx.getFileName());
					if (at != null) TransformUtil.makeAccessible(ctx.getData(), at);

					zfw2.writeNamed(ctx.getFileName(), ctx.getCompressedShared());
				}
			}

			bar.setName("保存");
			bar.increment();

			if (!atList.isEmpty()) LOGGER.error("未成功应用的AT: {}", atList);

			var hash = Integer.toHexString(CRC32.crc32(IOUtil.read(com2int)));

			var mapCache = new File(DATA_PATH, ".MC"+mcVersion+"FG"+forgeVersion+(serverSrgCombined!=null?"+S":"")+"_map"+hash+".lzma");
			try (var fs = new FileSource(mapCache)) {
				mapper.reverseSelf();
				mapper.saveCache(fs, 1);
			}

			var workspace = new Workspace();
			workspace.type = "Minecraft/ForgeV2";
			workspace.id = "MC"+mcVersion+"Forge"+forgeVersion+(serverSrgCombined != null?"+S":"")+"_Map"+hash;
			workspace.depend = Collections.singletonList(libraries);
			workspace.mappedDepend = Collections.singletonList(combinedCacheMcp);
			workspace.unmappedDepend = Collections.singletonList(combinedCache);
			workspace.mapping = mapCache;
			return workspace;
		}

		private String combine(File libraryPath, List<String> arguments, List<Context> contexts, ZipFileWriter CombinedCache, boolean skipUniversal) throws IOException {
			var launchTarget = arguments.get(arguments.indexOf("--launchTarget")+1).replace("forge", "");
			var mcVersion = arguments.get(arguments.indexOf("--fml.mcVersion")+1);
			var mcpVersion = arguments.get(arguments.indexOf("--fml.mcpVersion")+1);
			var forgeVersion = arguments.get(arguments.indexOf("--fml.forgeVersion")+1);
			var combinedSrgName = mcVersion+"-"+mcpVersion;
			var combinedForgeName = mcVersion+"-"+forgeVersion;

			var patchedSrg = new File(libraryPath, "net/minecraft/"+launchTarget+"/"+combinedSrgName+"/"+launchTarget+"-"+combinedSrgName+"-srg.jar");
			var forgeSided = new File(libraryPath, "net/minecraftforge/forge/"+combinedForgeName+"/forge-"+combinedForgeName+"-"+launchTarget+".jar");
			var forgeUniversal = new File(libraryPath, "net/minecraftforge/forge/"+combinedForgeName+"/forge-"+combinedForgeName+"-universal.jar");

			if (!patchedSrg.isFile() || !forgeSided.isFile() || !forgeUniversal.isFile()) {
				Terminal.error("无法找到必须的文件: "+patchedSrg+" || "+forgeSided+" || "+forgeUniversal);
				return null;
			}

			var PatchSrg = new ZipFile(patchedSrg, ZipFile.FLAG_BACKWARD_READ|ZipFile.FLAG_VERIFY);
			var ForgeSided = new ZipFile(forgeSided, ZipFile.FLAG_BACKWARD_READ|ZipFile.FLAG_VERIFY);
			var ForgeUniversal = new ZipFile(forgeUniversal, ZipFile.FLAG_BACKWARD_READ|ZipFile.FLAG_VERIFY);

			var fileList = new MyHashMap<String, ZipFile>();

			var bar = new EasyProgressBar("处理游戏代码");
			bar.addTotal(PatchSrg.entries().size());
			if (!skipUniversal) bar.addTotal(ForgeUniversal.entries().size());
			bar.addTotal(ForgeSided.entries().size());

			for (ZEntry entry : PatchSrg.entries()) {
				if (!entry.isDirectory())
					fileList.put(entry.getName(), PatchSrg);
				bar.increment();
			}
			if (!skipUniversal) {
				for (ZEntry entry : ForgeUniversal.entries()) {
					if (!entry.isDirectory()) {
						if (!entry.getName().startsWith("META-INF/")) {
							fileList.put(entry.getName(), ForgeUniversal);
						} else if (entry.getName().endsWith(".cfg")) {
							atBytes = ForgeUniversal.get(entry);
						}
					}
					bar.increment();
				}
			}
			for (ZEntry entry : ForgeSided.entries()) {
				if (!entry.isDirectory())
					fileList.put(entry.getName(), ForgeSided);
				bar.increment();
			}

			bar.setTotal(fileList.size());
			try {
				for (var entry : fileList.entrySet()) {
					if (entry.getKey().endsWith(".class")) {
						contexts.add(new Context(entry.getKey(), entry.getValue().get(entry.getKey())));
					} else {
						CombinedCache.copy(entry.getValue(), entry.getValue().getEntry(entry.getKey()));
					}
					bar.increment();
				}
				bar.end("成功");
			} finally {
				bar.close();
				IOUtil.closeSilently(PatchSrg);
				IOUtil.closeSilently(ForgeSided);
				IOUtil.closeSilently(ForgeUniversal);
			}

			return combinedForgeName;
		}

		@Override
		boolean mergeLibraryHook(String libName, File file) {return false;}
	}

	abstract boolean mergeLibraryHook(String libName, File file);
	final void combineLibrary(MinecraftClientInfo info, File libraryFileName) throws IOException {
		EasyProgressBar bar = new EasyProgressBar("复制库文件");
		bar.setTotal(info.libraries.size());

		MyHashMap<String, String> dupChecker = new MyHashMap<>();
		try (var zfw = new ZipFileWriter(libraryFileName)) {
			for (var itr = info.libraries.iterator(); itr.hasNext(); bar.increment(1)) {
				String libName = itr.next().path;

				File file = new File(info.libraryPath, libName);
				if (!file.isFile()) {
					LOGGER.error("找不到依赖 {}", libName);
					continue;
				}

				if (mergeLibraryHook(libName, file) || libName.startsWith("com/mojang/patchy")) {
					LOGGER.debug("跳过 {}", libName);
					continue;
				}

				try (var mzf = new ZipArchive(file, ZipArchive.FLAG_BACKWARD_READ)) {
					for (ZEntry entry : mzf.entries()) {
						if (entry.getName().endsWith(".class") && !entry.getName().endsWith("module-info.class")) {
							String prevPkg = dupChecker.get(entry.getName());
							if (prevPkg != null) {
								if (!libName.equals(prevPkg)) {
									prevPkg = prevPkg.substring(prevPkg.lastIndexOf('/') + 1);
									libName = libName.substring(libName.lastIndexOf('/') + 1);
									LOGGER.warn("在依赖{}和{}中找到了相同的类{}", prevPkg, libName, entry.getName());
								}
							} else {
								zfw.copy(mzf, entry);
								dupChecker.put(entry.getName(), libName);
							}
						}
					}
				}
			}

			bar.end("成功");
		} catch (Exception e) {
			bar.end("失败", Terminal.RED);
			throw e;
		} finally {
			bar.close();
		}
	}
}
