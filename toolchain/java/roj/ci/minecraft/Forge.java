package roj.ci.minecraft;

import roj.archive.zip.ZipEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipPacker;
import roj.asm.MemberDescriptor;
import roj.asmx.Context;
import roj.asmx.TransformUtil;
import roj.asmx.mapper.Mapper;
import roj.asmx.mapper.Mapping;
import roj.ci.Env;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.collect.TrieTreeSet;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.text.ParseException;
import roj.ui.Argument;
import roj.ui.EasyProgressBar;
import roj.ui.Tty;
import roj.util.function.ExceptionalSupplier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static roj.ci.MCMake.CACHE_PATH;
import static roj.ci.MCMake.log;
import static roj.ui.TUI.*;

/**
 * @author Roj234
 * @since 2025/10/09 20:44
 */
final class Forge extends MinecraftWorkspace {
	byte[] atBytes;

	@Override
	Env.Workspace init(EasyProgressBar bar, MinecraftClientInfo clientInfo) throws IOException {
		File com2int = input(text("指定映射配置(*.yml)或手动提供【调试名到中间名】映射表(*.srg) （必须）"), Argument.file());
		if (com2int == null) return null;
		File serverPath = inputOpt(text("同版本的Forge服务端目录（可选，用于服务端开发）"), Argument.folder());

		stepSuccess(text("信息收集完毕，正在处理，请耐心等待"));

		File tempArchive = new File(CACHE_PATH, "~ws-temp~"+Long.toString(System.nanoTime(), 36)+".jar");

		bar.setTotal(5);
		var resources = new ZipPacker(new FileSource(tempArchive), 8);
		var clientData = new ArrayList<Context>();

		Mapper mapper;

		if (IOUtil.getExtension(com2int.getName()).equals("yml")) {
			var context = new HashMap<String, ExceptionalSupplier<File, IOException>>();
			var arguments = clientInfo.gameArguments;
			var mcVersion = arguments.get(arguments.indexOf("--fml.mcVersion")+1);
			var mcpVersion = arguments.get(arguments.indexOf("--fml.mcpVersion")+1);
			context.put("@mojang_client", mapDownloader(clientInfo.gameCoreDownloads.getMap("client_mappings").getString("url")));
			context.put("@mojang_server", mapDownloader(clientInfo.gameCoreDownloads.getMap("server_mappings").getString("url")));
			context.put("@mcp_config", mapDownloader("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/"+mcVersion+"-"+mcpVersion+"/mcp_config-"+mcVersion+"-"+mcpVersion+".zip"));
			context.put("@fabric_intermediary", mapDownloader("https://github.com/FabricMC/intermediary/raw/refs/heads/master/mappings/"+mcVersion+".tiny"));
			context.put("@fabric_yarn", mapDownloader("https://codeload.github.com/FabricMC/yarn/zip/refs/heads/"+mcVersion));

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
			var serverSrgCombined = combine(new File(serverPath, "libraries"), arguments, serverData, resources, true);
			if (serverSrgCombined == null) return null;

			var merger = new ClassMerger();
			clientData = new ArrayList<>(merger.process(clientData, serverData));
			log.debug("ClassMerger: {}/{} entries, {} combined", merger.clientOnly, merger.both, merger.mergedField+merger.mergedMethod+merger.replaceMethod);
		}

		try (var writeProgress = new EasyProgressBar("压缩Mapped依赖")) {
			writeProgress.setTotal(clientData.size());
			for (int i = 0; i < clientData.size(); i++) {
				var ctx = clientData.get(i);
				resources.writeNamed(ctx.getFileName(), ctx.getCompressedShared());
				writeProgress.increment();
			}
		} finally {
			resources.close();
		}

		var combinedCache = new File(CACHE_PATH, "gen-"+mcVersion+"-Forge"+forgeVersion+"_srg.jar");
		IOUtil.copyOrMove(tempArchive, combinedCache, true);

		bar.setName("映射");
		bar.increment();

		var atList = AT.buildATMapFromATCfg(new ByteArrayInputStream(atBytes), mapper);
		for (int i = 0; i < clientData.size(); i++) {
			var ctx = clientData.get(i);

			var at = atList.remove(ctx.getFileName());
			if (at != null) TransformUtil.makeAccessible(ctx.getData(), at);
		}
		if (!atList.isEmpty()) log.error("未成功应用的AT: {}", atList);

		mapper.loadLibraries(Arrays.asList(libraries, combinedCache));
		mapper.packup();
		mapper.map(clientData);

		var combinedCacheMcp = new File(CACHE_PATH, "gen-"+mcVersion+"-Forge"+forgeVersion+"_mcp.jar");
		try (var zfw = new ZipPacker(combinedCacheMcp);
			 var writeProgress = new EasyProgressBar("压缩Unmapped依赖")) {
			writeProgress.setTotal(clientData.size());
			for (int i = 0; i < clientData.size(); i++) {
				var ctx = clientData.get(i);
				zfw.writeNamed(ctx.getFileName(), ctx.getCompressedShared());
				writeProgress.increment();
			}
		}

		bar.setName("压缩映射表");
		bar.increment();

		var mapCache = new File(CACHE_PATH, "gen-"+mcVersion+"-Forge"+forgeVersion+"_map.lzma");
		try (var fs = new FileSource(mapCache)) {
			// forge's patch
			mapper.getMethodMap().keySet().removeIf(desc -> desc.modifier == MemberDescriptor.FLAG_UNSET);
			mapper.getFieldMap().keySet().removeIf(desc -> desc.modifier == MemberDescriptor.FLAG_UNSET);
			mapper.reverseSelf();
			mapper.saveCache(fs, 1);
		}

		var workspace = new Env.Workspace();
		workspace.type = "minecraft/forge";
		workspace.id = mcVersion+"-Forge"+forgeVersion;
		workspace.depend = Collections.singletonList(libraries);
		workspace.mappedDepend = Collections.singletonList(combinedCacheMcp);
		workspace.unmappedDepend = Collections.singletonList(combinedCache);
		workspace.mapping = mapCache;
		workspace.processors = new ArrayList<>();
		workspace.processors.add("roj.ci.plugin.MAP");
		workspace.processors.add("roj.ci.minecraft.AT");
		workspace.processors.add("roj.ci.minecraft.MIXIN");
		workspace.variableReplaceContext = new TrieTreeSet("META-INF/MANIFEST.MF", "META-INF/mods.toml", "pack.mcmeta");
		workspace.variables = new LinkedHashMap<>();
		workspace.variables.put("mc_version", mcVersion);
		workspace.variables.put("forge_version", forgeVersion);
		workspace.variables.put("forge_major", forgeVersion.substring(0, forgeVersion.indexOf('.')));
		return workspace;
	}

	private String combine(File libraryPath, List<String> arguments, List<Context> contexts, ZipPacker CombinedCache, boolean skipUniversal) throws IOException {
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

		var PatchSrg = new ZipFile(patchedSrg, ZipFile.FLAG_ReadCENOnly | ZipFile.FLAG_Verify);
		var ForgeSided = new ZipFile(forgeSided, ZipFile.FLAG_ReadCENOnly | ZipFile.FLAG_Verify);
		var ForgeUniversal = new ZipFile(forgeUniversal, ZipFile.FLAG_ReadCENOnly | ZipFile.FLAG_Verify);

		var fileList = new HashMap<String, ZipFile>();

		var bar = new EasyProgressBar("合并Forge代码");
		bar.addTotal(PatchSrg.entries().size());
		if (!skipUniversal) bar.addTotal(ForgeUniversal.entries().size());
		bar.addTotal(ForgeSided.entries().size());

		for (ZipEntry entry : PatchSrg.entries()) {
			if (!entry.isDirectory())
				fileList.put(entry.getName(), PatchSrg);
			bar.increment();
		}
		if (!skipUniversal) {
			for (ZipEntry entry : ForgeUniversal.entries()) {
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
		for (ZipEntry entry : ForgeSided.entries()) {
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
		} finally {
			bar.close();
			IOUtil.closeSilently(PatchSrg);
			IOUtil.closeSilently(ForgeSided);
			IOUtil.closeSilently(ForgeUniversal);
		}

		return combinedForgeName;
	}

	@Override
	boolean mergeLibraryHook(String libName, File file) {
		return false;
	}
}
