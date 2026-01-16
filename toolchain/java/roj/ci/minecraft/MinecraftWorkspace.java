package roj.ci.minecraft;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZipEditor;
import roj.archive.zip.ZipEntry;
import roj.archive.zip.ZipPacker;
import roj.ci.Env;
import roj.collect.HashMap;
import roj.config.node.MapValue;
import roj.http.curl.DownloadTask;
import roj.io.IOUtil;
import roj.text.ParseException;
import roj.ui.*;
import roj.util.Helpers;
import roj.util.function.ExceptionalSupplier;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static roj.ci.MCMake.CACHE_PATH;
import static roj.ci.MCMake.log;
import static roj.ui.TUI.*;

/**
 * @author Roj234
 * @since 2025/2/12 3:47
 */
public abstract sealed class MinecraftWorkspace permits Fabric, ForgeLegacy, Forge {
	@Nullable
	public static Env.Workspace build(MapValue config) {
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
					TUI.stepInfo(text("目标模组加载器："+instance.getClass().getSimpleName()));
					try (var bar = new EasyProgressBar()) {
						var result = instance.init(bar, clientInfo);
						if (result != null) {
							TUI.end(text("构建成功"));
							return result;
						}
					}
				} else {
					TUI.stepError(text("未识别到MCMake支持的模组加载器"));
				}
			} catch (IOException | ParseException e) {
				e.printStackTrace();
			}
		}

		TUI.end(text("构建失败"));
		return null;
	}

	static ExceptionalSupplier<File, IOException> mapDownloader(String downloadUrl) {
		return () -> {
			try {
				File cache = new File(CACHE_PATH, ".temp/"+ Helpers.sha1Hash(downloadUrl)+"."+IOUtil.getExtension(downloadUrl));
				cache.getParentFile().mkdirs();
				return cache.isFile() ? cache : DownloadTask.download(downloadUrl, cache).get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		};
	}

	abstract Env.Workspace init(EasyProgressBar bar, MinecraftClientInfo clientInfo) throws IOException;

	private static MinecraftWorkspace findProperInstance(MinecraftClientInfo info) {
		if (info.libraries.containsKey("cpw/mods:modlauncher")) {
			return new Forge();
		} else if (info.libraries.containsKey("net/minecraftforge:forge")) {
			return new ForgeLegacy();
		} else if (info.libraries.containsKey("net/fabricmc:fabric-loader")) {
			return new Fabric();
		}

		return null;
	}

	abstract boolean mergeLibraryHook(String libName, File file);
	final void combineLibrary(MinecraftClientInfo info, File libraryFileName) throws IOException {
		EasyProgressBar bar = new EasyProgressBar("合并依赖");
		bar.setTotal(info.libraries.size());

		HashMap<String, String> dupChecker = new HashMap<>();
		try (var zfw = new ZipPacker(libraryFileName)) {
			for (var itr = info.libraries.iterator(); itr.hasNext(); bar.increment(1)) {
				String libName = itr.next().path;

				File file = new File(info.libraryPath, libName);
				if (!file.isFile()) {
					log.error("找不到依赖 {}", libName);
					continue;
				}

				if (mergeLibraryHook(libName, file) || libName.startsWith("com/mojang/patchy")) {
					log.debug("跳过 {}", libName);
					continue;
				}

				try (var mzf = new ZipEditor(file, ZipEditor.FLAG_ReadCENOnly)) {
					for (ZipEntry entry : mzf.entries()) {
						if (entry.getName().endsWith(".class") && !entry.getName().endsWith("module-info.class")) {
							String prevPkg = dupChecker.get(entry.getName());
							if (prevPkg != null) {
								if (!libName.equals(prevPkg)) {
									prevPkg = prevPkg.substring(prevPkg.lastIndexOf('/') + 1);
									libName = libName.substring(libName.lastIndexOf('/') + 1);
									log.warn("在依赖{}和{}中找到了相同的类{}", prevPkg, libName, entry.getName());
								}
							} else {
								zfw.copy(mzf, entry);
								dupChecker.put(entry.getName(), libName);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			bar.end("失败", Tty.RED);
			throw e;
		} finally {
			bar.close();
		}
	}
}
