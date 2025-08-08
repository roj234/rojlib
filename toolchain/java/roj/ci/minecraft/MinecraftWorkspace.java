package roj.ci.minecraft;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.MemberDescriptor;
import roj.asmx.Context;
import roj.asmx.TransformUtil;
import roj.asmx.mapper.Mapper;
import roj.asmx.mapper.Mapping;
import roj.ci.Workspace;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.io.source.ByteSource;
import roj.io.source.FileSource;
import roj.ui.*;
import roj.util.function.ExceptionalSupplier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static roj.ci.FMD.CACHE_PATH;
import static roj.ci.FMD.LOGGER;
import static roj.ui.TUI.*;

/**
 * @author Roj234
 * @since 2025/2/12 3:47
 */
public abstract sealed class MinecraftWorkspace {
	@Nullable
	public static Workspace build(CMap config) {
		start(text("构建Minecraft工作空间"));

		File mcRoot = new File(config.getString("MC路径"));
		if (!mcRoot.isDirectory()) mcRoot = input(text("MC目录(.minecraft)"), Argument.folder());
		if (!new File(mcRoot, "versions").isDirectory()) mcRoot = new File(mcRoot, ".minecraft");

		File versionPath = new File(mcRoot, "versions");
		var versions = MinecraftClientInfo.listVersions(versionPath);

		String mcJson;
		if (versions.isEmpty()) {
			stepError(text("没有找到任何MC版本！请确认目录是否正确"));
		} else {
			mcJson = config.getString("MC版本");
			if (!versions.contains(mcJson)) {
				mcJson = radio(text("MC版本"), versions, Completion::new);
			}

			var clientInfo = new MinecraftClientInfo();
			clientInfo.libraryPath = new File(mcRoot, "libraries");
			try {
				clientInfo.resolve(versionPath, mcJson);
				MinecraftWorkspace instance = findProperInstance(clientInfo);
				if (instance != null) {
					try (var bar = new EasyProgressBar("初始化"+instance.getClass().getSimpleName()+"工作空间")) {
						Workspace ws = instance.init(bar, clientInfo);
						if (ws != null) {
							TUI.end(text("构建成功"));
							return ws;
						}
					}
				}
			} catch (IOException | ParseException e) {
				e.printStackTrace();
			}
		}

		TUI.end(text("构建失败"));
		return null;
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
			File com2int = input(text("指定映射配置(*.yml)或手动提供【调试名到中间名】映射表(*.srg) （必须）"), Argument.file());
			if (com2int == null) return null;
			File serverPath = inputOpt(text("同版本的Forge服务端（可选，用于服务端开发）"), Argument.file());

			stepSuccess(text("信息收集完毕，正在处理，请耐心等待"));

			bar.setTotal(5);
			var tmpSource = new ByteSource();
			var resources = new ZipFileWriter(tmpSource, 8);
			var clientData = new ArrayList<Context>();
			var context = new HashMap<String, ExceptionalSupplier<File, IOException>>();
/*			var serverMappingsUrl = clientInfo.gameCoreDownloads.getMap("server_mappings").getString("url");
			var clientMappingsUrl = clientInfo.gameCoreDownloads.getMap("client_mappings").getString("url");
			context.put("client.txt", () -> {
				try {
					return DownloadTask.download(clientMappingsUrl, new File("fmdTemp.txt")).get();
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e);
				}
			});
			context.put("server.txt", () -> {
				try {
					return DownloadTask.download(serverMappingsUrl, new File("fmdTemp.txt")).get();
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e);
				}
			});*/

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

			File libraries = new File(CACHE_PATH, "gen-"+mcVersion+"-Forge"+forgeVersion+"_lib.jar");
			combineLibrary(clientInfo, libraries);

			bar.setName("服务端");
			bar.increment();

			String serverSrgCombined = null;
			if (serverPath != null) {
				var argumentFile = new File(serverPath, "libraries/net/minecraftforge/forge/"+clientForgeVersion+"/win_args.txt");
				if (!argumentFile.isFile()) {
					Tty.warning("服务端加载失败：找不到这个文件："+argumentFile);
					Tty.warning("请确认服务端和客户端是相同版本"+clientForgeVersion+"且能运行");
					return null;
				}

				var text = IOUtil.readString(argumentFile);
				var arguments = MinecraftClientInfo.tokenize(text);

				var serverData = new ArrayList<Context>();
				serverSrgCombined = combine(new File(serverPath, "libraries"), arguments, serverData, resources, true);
				if (serverSrgCombined == null) return null;

				var merger = new ClassMerger();
				clientData = new ArrayList<>(merger.process(clientData, serverData));
				LOGGER.debug("ClassMerger: {}/{} entries, {} combined", merger.clientOnly, merger.both, merger.mergedField+merger.mergedMethod+merger.replaceMethod);
			}

			for (int i = 0; i < clientData.size(); i++) {
				var ctx = clientData.get(i);
				resources.writeNamed(ctx.getFileName(), ctx.getCompressedShared());
			}
			resources.close();
			var combinedCache = new File(CACHE_PATH, "gen-"+mcVersion+"-Forge"+forgeVersion+"_srg.jar");
			try (var fos = new FileOutputStream(combinedCache)) {
				tmpSource.buffer().writeToStream(fos);
			}

			bar.setName("映射");
			bar.increment();

			Mapper mapper;

			if (IOUtil.extensionName(com2int.getName()).equals("yml")) {
				Mapping mapping;
				try {
					mapping = new MappingBuilder(com2int.getParentFile(), context).build(com2int);
				} catch (ParseException e) {
					throw new RuntimeException("MappingBuilder解析失败", e);
				}
				mapper = new Mapper(mapping);
				for (var itr = mapper.getParamMap().values().iterator(); itr.hasNext(); ) {
					for (String param : itr.next()) {
						if (param != null && param.startsWith("p_")) {
							itr.remove();
							break;
						}
					}
				}
			} else {
				mapper = new Mapper();
				mapper.loadMap(com2int, true);
			}

			mapper.loadLibraries(Arrays.asList(libraries, combinedCache));
			mapper.packup();
			var atList = AT.buildATMapFromATCfg(new ByteArrayInputStream(atBytes), mapper);

			var combinedCacheMcp = new File(CACHE_PATH, "gen-"+mcVersion+"-Forge"+forgeVersion+"_mcp.jar");
			try (var zfw2 = new ZipFileWriter(combinedCacheMcp)) {
				mapper.flag |= Mapper.MF_SINGLE_THREAD; // 多线程不知道什么时候又出bug了，暂时先禁用
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

			var mapCache = new File(CACHE_PATH, "gen-"+mcVersion+"-Forge"+forgeVersion+"_map.lzma");
			try (var fs = new FileSource(mapCache)) {
				// forge's patch
				mapper.getMethodMap().keySet().removeIf(desc -> desc.modifier == MemberDescriptor.FLAG_UNSET);
				mapper.getFieldMap().keySet().removeIf(desc -> desc.modifier == MemberDescriptor.FLAG_UNSET);
				mapper.reverseSelf();
				mapper.saveCache(fs, 1);
			}

			var workspace = new Workspace();
			workspace.type = "Minecraft/ForgeV2";
			workspace.id = mcVersion+"-Forge"+forgeVersion;
			workspace.depend = Collections.singletonList(libraries);
			workspace.mappedDepend = Collections.singletonList(combinedCacheMcp);
			workspace.unmappedDepend = Collections.singletonList(combinedCache);
			workspace.mapping = mapCache;
			workspace.processors = new ArrayList<>();
			workspace.processors.add("roj.ci.plugin.MAP");
			workspace.processors.add("roj.ci.plugin.AT");
			workspace.processors.add("roj.ci.plugin.MIXIN");
			workspace.variables = new HashMap<>();
			workspace.variables.put("mc_version", mcVersion);
			workspace.variables.put("forge_version", forgeVersion);
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
				Tty.error("无法找到必须的文件: "+patchedSrg+" || "+forgeSided+" || "+forgeUniversal);
				return null;
			}

			var PatchSrg = new ZipFile(patchedSrg, ZipFile.FLAG_BACKWARD_READ|ZipFile.FLAG_VERIFY);
			var ForgeSided = new ZipFile(forgeSided, ZipFile.FLAG_BACKWARD_READ|ZipFile.FLAG_VERIFY);
			var ForgeUniversal = new ZipFile(forgeUniversal, ZipFile.FLAG_BACKWARD_READ|ZipFile.FLAG_VERIFY);

			var fileList = new HashMap<String, ZipFile>();

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

		HashMap<String, String> dupChecker = new HashMap<>();
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
			bar.end("失败", Tty.RED);
			throw e;
		} finally {
			bar.close();
		}
	}
}
