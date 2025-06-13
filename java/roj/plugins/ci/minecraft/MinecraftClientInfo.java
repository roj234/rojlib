package roj.plugins.ci.minecraft;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFile;
import roj.collect.ArrayList;
import roj.collect.TrieTreeSet;
import roj.collect.XashMap;
import roj.concurrent.Promise;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.Tokenizer;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.config.data.Type;
import roj.http.server.HSConfig;
import roj.io.IOUtil;
import roj.math.Version;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.Terminal;
import roj.util.ArrayCache;
import roj.util.Helpers;
import roj.util.OS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import static roj.plugins.ci.FMD.LOGGER;

/**
 * @author Roj234
 * @since 2025/2/12 5:02
 */
final class MinecraftClientInfo {
	static final class Library {
		final String name;
		boolean isNative;
		String path;
		Version version;

		Library _next;

		Library(String name) {this.name = name;}
	}

	private static final XashMap.Builder<String, Library> BUILDER = XashMap.builder(String.class, Library.class, "name", "_next");

	File libraryPath;
	XashMap<String, Library> libraries = BUILDER.create();
	Function<String, Promise<File>> downloader;
	File nativePath;

	String id;
	int version;
	String assets;
	File jar;
	String mainClass;
	List<String> jvmArguments, gameArguments;
	CMap gameCoreDownloads;

	public String libraryString() {
		var libPathString = new CharList();
		String path = libraryPath.getAbsolutePath()+"/";
		for (var lib : libraries) {
			libPathString.append(path).append(lib.path).append(File.pathSeparatorChar);
		}
		return libPathString.toStringAndFree();
	}

	public static List<String> listVersions(File file) {return listVersions(file, new ArrayList<>());}
	private static List<String> listVersions(File file, List<String> jsons) {
		File[] files = file.listFiles();
		if (files != null) {
			for (File mcDir : files) {
				if (mcDir.isDirectory()) {
					File mcJson = new File(mcDir, mcDir.getName()+".json");
					if (mcJson.isFile()) jsons.add(mcDir.getName());
				}
			}
		}
		return jsons;
	}

	MinecraftClientInfo resolve(File versionPath, String version) throws IOException, ParseException {
		CMap mapping = ConfigMaster.JSON.parse(new File(versionPath, version+'/'+version+".json")).asMap();

		String inheritVersion = mapping.getString("inheritsFrom");
		if (inheritVersion.length() > 0) resolve(versionPath, inheritVersion);

		gameCoreDownloads = mapping.getMap("downloads");

		var libraries = mapping.getList("libraries").raw();
		for (int i = 0; i < libraries.size(); i++) {
			mergeLibrary(libraries.get(i).asMap());
		}

		this.id = mapping.getString("id");

		String jar = mapping.getString("jar");
		if (!jar.isEmpty()) this.jar = new File(versionPath, jar + '/' + jar + ".jar");

		String assets = mapping.getString("assets");
		if (!assets.isEmpty()) this.assets = assets;

		String mainClass = mapping.getString("mainClass");
		if (!mainClass.isEmpty()) this.mainClass = mainClass;

		int ver = mapping.getInt("minimumLauncherVersion");
		if (ver != 0) this.version = ver;

		if (ver < 18) {
			var arguments = mapping.getString("minecraftArguments");
			if (!arguments.isEmpty()) {
				jvmArguments = Arrays.asList("-Djava.library.path=${natives_directory}", "-cp", "${classpath}");
				gameArguments = tokenize(arguments);
			}
		} else {
			var arguments = mapping.getMap("arguments");
			jvmArguments = makeNewArgs(arguments.getList("jvm"));
			gameArguments = makeNewArgs(arguments.getList("game"));
		}

		return this;
	}
	@SuppressWarnings("unchecked")
	private static List<String> makeNewArgs(CList argList) {
		var args = new ArrayList<String>();

		for (var entry : argList) {
			switch (entry.getType()) {
				case STRING -> args.add(entry.asString());
				case MAP -> {
					if (fitRules(entry.asMap().getList("rules"))) {
						CEntry argument = entry.asMap().get("value");
						if (argument.getType() == Type.LIST) {
							args.addAll((List<String>) argument.asList().unwrap());
						} else {
							args.add(argument.asString());
						}
					}
				}
				default -> throw new IllegalArgumentException("未知类型: " + entry);
			}
		}
		return args;
	}
	static List<String> tokenize(String argString) {
		try {
			return Tokenizer.arguments().splitToString(argString);
		} catch (ParseException e) {
			LOGGER.warn("无法解析指令 {}: ", e, argString);
			return Collections.emptyList();
		}
	}

	private static final int NOT = -1, MAYBE = 0, DEFINITELY = 1;
	private void mergeLibrary(CMap libraryInfo) {
		String mavenId = libraryInfo.getString("name");
		int i = mavenId.indexOf(':');
		CharList sb = new CharList().append(mavenId).replace('.', '/', 0, i);
		List<String> parts = TextUtil.split(new ArrayList<>(4), sb, ':');

		String ext = "jar";
		final String s = parts.get(parts.size() - 1);
		int extPos = s.lastIndexOf('@');
		if (extPos != -1) {
			ext = s.substring(extPos + 1);
			parts.set(parts.size() - 1, s.substring(0, extPos));
		}

		sb.clear();
		String nameNoVersion = sb.append(parts.get(0)).append(':').append(parts.get(1)).toString();

		if (!fitRules(libraryInfo.getList("rules"))) {
			LOGGER.debug("{}不符合加载规则{},跳过", mavenId, libraryInfo.get("rules"));
			return;
		}

		var library = libraries.computeIfAbsent(nameNoVersion);
		var version = new Version(parts.get(2));
		boolean sameVer = false;
		if (library.version != null) {
			switch (version.compareTo(library.version)) {
				case -1 -> {
					LOGGER.debug("已有新版本{}的{}, 跳过旧版本{}", library.version, nameNoVersion, version);
					return;
				}
				case 0 -> sameVer = true;
				case 1 -> LOGGER.debug("已有新版本{}的{}, 跳过旧版本{}", version, nameNoVersion, library.version);
			}
		}
		library.version = version;

		sb.clear();
		sb.append(parts.get(0)).append('/') // d
		  .append(parts.get(1)).append('/') // n
		  .append(parts.get(2)).append('/') // v
		  .append(parts.get(1)).append('-').append(parts.get(2)); // n-v

		String classifier = null;
		int isNative = NOT;
		if (libraryInfo.containsKey("natives")) {
			CMap natives = libraryInfo.getMap("natives");
			sb.append('-').append(natives.getString(OS.CURRENT.name().toLowerCase(Locale.ROOT)).replace("${arch}", OS.archName()));
			isNative = DEFINITELY;
		} else if (parts.size() > 3) {
			classifier = parts.get(3);
			sb.append('-').append(classifier);
			if (classifier.startsWith("natives-")) isNative = MAYBE;
			else {
				String rules = libraryInfo.getList("rules").toString();
				if (rules.contains("\"allow\"") && rules.contains("\"os\"")) isNative = MAYBE;
			}
		}

		String filePath = sb.append('.').append(ext).toString();
		File libFile = new File(libraryPath, filePath);

		if (!libFile.isFile()) {
			if (downloader == null) {
				LOGGER.warn("依赖丢失: {}", filePath);
			} else {
				 downloadLibrary(libraryInfo, classifier, filePath);
			}
			return;
		}

		if (isNative == DEFINITELY || (isNative == MAYBE && checkNativesOnly(libFile))) {
			library.isNative = true;
			if (nativePath != null) extractNatives(libraryInfo, libFile);
			return;
		}

		// Log4j2漏洞
		if (nameNoVersion.endsWith("log4j-core")) fixLog4j(libFile);

		if (sameVer) {
			LOGGER.info("遇到了相同版本的 {}, 跳过", nameNoVersion);
			return;
		}
		library.path = filePath;
	}

	private static boolean checkNativesOnly(File libFile) {
		try (var zf = new ZipFile(libFile)) {
			int nativeCount = 0;
			int classCount = 0;
			for (ZEntry entry : zf.entries()) {
				if (entry.getName().endsWith(".class")) classCount++;
				if (entry.getName().endsWith(".dll") || entry.getName().endsWith(".so")) nativeCount++;
			}
			return nativeCount > classCount;
		} catch (IOException ignored) {}
		return false;
	}
	private void extractNatives(CMap libraryInfo, File libFile) {
		nativePath.mkdirs();

		var trieTree = new TrieTreeSet();
		for (CEntry entry : libraryInfo.getMap("extract").getList("exclude")) trieTree.add(entry.asString());
		trieTree.add("META-INF");

		LOGGER.debug("提取本地库{}", libFile);
		try (var zf = new ZipFile(libFile)) {
			for (ZEntry entry : zf.entries()) {
				final String name = entry.getName();
				if (!entry.isDirectory() && !trieTree.strStartsWithThis(name) && (name.endsWith(".dll") || name.endsWith(".so"))) {
					try (var fos = new FileOutputStream(new File(nativePath, name))) {
						IOUtil.copyStream(zf.getStream(entry), fos);
					}
				} else {
					LOGGER.debug("排除文件{}#!{}", libFile, entry);
				}

			}
		} catch (IOException e) {
			LOGGER.warn("无法读取本地库{}", e, libFile);
		}
	}

	private static void fixLog4j(File lib) {
		try (ZipArchive mzf = new ZipArchive(lib)) {
			byte[] b = mzf.get("org/apache/logging/log4j/core/lookup/JndiLookup.class");
			if (b != null) mzf.put("org/apache/logging/log4j/core/lookup/JndiLookup.class", null);
		} catch (Throwable e) {
			Terminal.warning("无法修补Log4j2漏洞: ", e);
		}
	}

	private static boolean fitRules(CList rules) {
		boolean fit = rules.size() == 0;
		for (int i = 0; i < rules.size(); i++) {
			CMap rule = rules.getMap(i);
			if (fitRule(rule)) {
				fit = rule.getString("action").equals("allow");
			}
		}
		return fit;
	}
	private static boolean fitRule(CMap rule) {
		if (rule.size() == 1) return true;
		if (rule.containsKey("os")) {
			CMap os = rule.getMap("os");

			String name = os.getString("name");
			if (!name.isEmpty() && !switch (name) {
				case "osx" -> OS.CURRENT == OS.OSX;
				case "win", "windows" -> OS.CURRENT == OS.WINDOWS;
				case "linux" -> OS.CURRENT == OS.UNIX;
				default -> OS.CURRENT == OS.UNKNOWN || OS.CURRENT == OS.JVM;
			}) return false;

			String version = os.getString("version");
			if (!version.isEmpty()) {
				try {
					Pattern pattern = Pattern.compile(version);
					String test = System.getProperty("os.name").replace("Windows ", "");
					if (!pattern.matcher(test).matches()) return false;
				} catch (Throwable ignored) {}
			}

			String arch = os.getString("arch");
			if (!arch.isEmpty() && !arch.equals(OS.archName())) return false;
		}

		if (rule.containsKey("features")) {
			for (var entry : rule.getMap("features").entrySet()) {
				String name = entry.getKey();
				if (name.contains("quick_play")) return false;
				switch (name) {
					case "is_demo_user", "has_custom_resolution":
						return false;
				}

				CEntry value = entry.getValue();
				Terminal.warning("FMD发现了一个未知规则: ");
				Terminal.warning(name + ": " + value);
				if (!Terminal.readBoolean("请输入y或n并按回车: ")) return false;
			}
		}

		return true;
	}

	static int RETRY_COUNT = 5;
	static class DownloadTask implements BiConsumer<File, Promise.Callback>, Function<Throwable, Object> {
		File savedFile;

		String url;
		long size;
		String sha1;
		int retry;

		@Override
		public void accept(File file, Promise.Callback callback) {
			if (size != 0 && file.length() != size) throw new IllegalStateException("大小校验失败");

			if (!sha1.isEmpty()) {
				var digest = HSConfig.getInstance().sha1();

				var buf = ArrayCache.getByteArray(4096, false);
				try (var in = new FileInputStream(file)) {
					int r;
					while (true) {
						r = in.read(buf);
						if (r < 0) break;
						digest.update(buf, 0, r);
					}
				} catch (Exception e) {
					throw new IllegalStateException("摘要校验失败", e);
				} finally {
					ArrayCache.putArray(buf);
				}

				if (!TextUtil.bytes2hex(digest.digest()).equalsIgnoreCase(sha1)) {
					throw new IllegalStateException("摘要校验失败");
				}
			}

			if (!file.renameTo(savedFile)) {
				try {
					IOUtil.copyFile(file, savedFile);
				} catch (IOException e) {
					retry = 9999;
					Helpers.athrow(e);
				}
				try {
					Files.delete(file.toPath());
				} catch (IOException e) {
					LOGGER.warn("临时文件删除失败，您可手动删除", e);
				}
			}
		}

		@Override
		public Object apply(Throwable throwable) {
			int p1 = ++retry;
			LOGGER.warn("依赖{}下载失败, 重试{}/{}", savedFile, p1, RETRY_COUNT);
			if (p1 > RETRY_COUNT) {
				LOGGER.warn("任务取消");
			} else {
				downloader.apply(url).then(this).rejected(this);
			}

			return null;
		}

		Function<String, Promise<File>> downloader;
		public void run(Function<String, Promise<File>> downloader) {
			this.downloader = downloader;
			downloader.apply(url).then(this).rejected(this);
		}
	}
	private void downloadLibrary(CMap libraryInfo, String classifiers, String libFileName) {
		File libParent = new File(libraryPath, libFileName).getParentFile();
		if (!libParent.isDirectory() && !libParent.mkdirs()) throw new IllegalStateException("无法创建保存文件夹 "+libParent.getAbsolutePath());

		CMap artifact = null;
		if (classifiers != null) {
			artifact = libraryInfo.getMap("classifiers").getMap(classifiers);
		}
		if (artifact == null || artifact.size() == 0) {
			if (!libraryInfo.containsKey("artifact")) { // mc
				artifact = new CMap();
				if (!libraryInfo.containsKey("url")) {
					//artifact.put("url", MAIN_CONFIG.get("通用").asMap().getString("协议") + "libraries.minecraft.net/" + libFileName);
				} else {
					artifact.put("url", libraryInfo.getString("url") + libFileName);
				}
			} else {
				artifact = libraryInfo.getMap("artifact");
			}
		}

		var task = new DownloadTask();
		task.url = artifact.getString("url");
		task.size = artifact.getLong("size");
		task.sha1 = artifact.getString("sha1");
		/*if (task.url.isEmpty()) {
			CMap map1 = CONFIG.get("通用").asMap();
			task.url = map1.getString("协议") + (map1.getString("libraries地址").isEmpty() ? map1.getString("forge地址") : map1.getString("libraries地址")) + '/' + libFileName;
		}*/
		task.run(downloader);
	}
}
