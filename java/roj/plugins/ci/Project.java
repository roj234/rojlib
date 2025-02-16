package roj.plugins.ci;

import roj.archive.ArchiveUtils;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.archive.zip.ZipOutput;
import roj.asmx.mapper.Mapper;
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.ScheduleTask;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.text.Formatter;
import roj.ui.Terminal;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @author Roj233
 * @since 2021/7/11 13:59
 */
public final class Project {
	public boolean compiling;

	FMD.EnvPojo.Project conf;

	final String name;
	String version;
	final Charset charset;
	List<Project> dependencies;
	final File root, srcPath, resPath, libPath;
	final List<File> binaryDepend;
	final Compiler compiler;
	public final Workspace workspace;
	public Mapper mapper;
	public Mapper.State mapperState;
	public final Map<String, String> variables;

	final File unmappedJar;
	private final String resPrefix;
	ZipOutput mappedWriter, unmappedWriter;
	FMD.SignatureCache signatureCache;

	public String getName() {return name;}
	public File getRoot() {return root;}
	public File getResPath() {return resPath;}

	public Project(FMD.EnvPojo.Project config) throws IOException {
		this.conf = config;
		this.name = IOUtil.safePath(config.name);
		this.version = config.version.toString();
		this.charset = config.charset;

		this.root = new File(FMD.PROJECT_PATH, name);
		this.srcPath = new File(root, "java");
		this.resPath = new File(root, "resources");
		this.libPath = new File(root, "libs");
		this.unmappedJar = new File(FMD.PROJECT_PATH, name+".jar");

		var obj = FMD.compilerTypes.get(config.compiler);
		if (obj == null) throw new NullPointerException("找不到编译器"+config.compiler);
		this.compiler =  obj.getInstance(srcPath.getAbsolutePath().replace(File.separatorChar, '/'));
		var obj2 = FMD.workspaces.get(config.workspace);
		if (obj2 == null) throw new NullPointerException("找不到工作空间"+config.workspace);
		this.workspace = obj2;

		this.variables = new MyHashMap<>(obj2.variables);
		this.variables.put("name", name);
		this.variables.put("version", version);
		this.variables.putAll(config.variables);

		this.binaryDepend = new SimpleList<>();
		for (String depend : conf.binary_depend) {
			binaryDepend.add(IOUtil.relativePath(depend.startsWith("/") ? FMD.BASE : libPath, depend));
		}

		if (root.mkdirs()) Workspace.addIDEAProject(this, false);

		srcPath.mkdir();
		resPath.mkdir();
		libPath.mkdir();
		unmappedJar.createNewFile();

		unmappedWriter = new ZipOutput(unmappedJar);
		unmappedWriter.setCompress(true);

		resPrefix = resPath.getAbsolutePath();
	}
	public void init() {
		LinkedMyHashMap<Project, Void> projects = new LinkedMyHashMap<>();

		List<Project> dest = new ArrayList<>();
		for (String name : conf.dependency) dest.add(FMD.projects.get(name));

		List<Project> dest2 = new ArrayList<>();
		while (!dest.isEmpty()) {
			for (int i = 0; i < dest.size(); i++) {
				Project key = dest.get(i);
				projects.put(key, null);

				for (String name : key.conf.dependency) dest2.add(FMD.projects.get(name));
			}
			List<Project> tmp = dest;
			dest = dest2;
			dest2 = tmp;
			dest2.clear();
		}

		this.dependencies = new SimpleList<>(projects.keySet());
		ArrayUtil.inverse(dependencies);
	}

	public List<Project> getAllDependencies() {return dependencies;}

	private int resCount;
	public Callable<Integer> getResourceTask(long stamp) {
		return () -> {
			resCount = 0;
			long useCompileTimestamp = "true".equals(variables.get("fmd:resource:use_compile_timestamp")) ? System.currentTimeMillis() : 0;
			boolean prevCompress = mappedWriter.isCompress();
			block: {
				if (stamp > 0) {
					Set<String> set = FMD.watcher.getModified(Project.this, FileWatcher.ID_RES);
					if (!set.contains(null)) {
						synchronized (set) {
							for (String fileName : set) writeRes(fileName, useCompileTimestamp != 0 ? useCompileTimestamp : new File(fileName).lastModified());
							set.clear();
						}
						break block;
					}
				}

				// update all resource ??
				IOUtil.findAllFiles(resPath, file -> {
					long lastModified = file.lastModified();
					if (lastModified >= stamp) writeRes(file.getAbsolutePath(), useCompileTimestamp != 0 ? useCompileTimestamp : lastModified);
					return false;
				});

				ZipFileWriter zfw = mappedWriter.getZFW();
				if (zfw != null) {
					for (var depend : binaryDepend) {
						try (var zf = new ZipFile(depend)) {
							for (ZEntry entry : zf.entries()) {
								if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;
								mappedWriter.getZFW().copy(zf, entry);
							}
						}
					}
				}
			}

			mappedWriter.setCompress(prevCompress);
			return resCount;
		};
	}
	private void writeRes(String absolutePath, long time) {
		resCount++;
		String relPath = absolutePath.substring(resPrefix.length()+1).replace(File.separatorChar, '/');
		mappedWriter.setCompress(!ArchiveUtils.INCOMPRESSIBLE_FILE_EXT.contains(IOUtil.extensionName(relPath)));
		try {
			if (conf.variable_replace_in.strStartsWithThis(relPath)) {
				String string;
				try {
					string = IOUtil.readString(new File(absolutePath));
				} catch (Exception e) {
					FMD.LOGGER.warn("变量替换规则可能命中了二进制文件{}", e, relPath);
					return;
				}

				var template = Formatter.simple(string);
				if (template.isDynamic()) {
					string = template.format(variables, IOUtil.getSharedCharBuf()).toString();
				}

				String fuckJavac = string;
				mappedWriter.set(relPath, () -> DynByteBuf.wrap(fuckJavac.getBytes(charset)), time);
				return;
			}

			mappedWriter.setStream(relPath, () -> {
				try {
					return new FileInputStream(absolutePath);
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
			}, time);
		} catch (IOException e) {
			FMD.LOGGER.warn("资源文件{}复制失败", e, relPath);
		}
	}

	private ScheduleTask delayedCompile;
	private boolean autoCompile;
	public void compileSuccess() {
		if (delayedCompile != null) delayedCompile.cancel();
		try {
			FMD.watcher.add(this);
		} catch (IOException e) {
			Terminal.warning("无法启动文件监控", e);
		}
	}
	public void setAutoCompile(boolean b) {autoCompile = b;}
	public boolean isAutoCompile() {return autoCompile;}
	public void fileChanged() {
		if (delayedCompile != null) delayedCompile.cancel();
		if (autoCompile) {
			delayedCompile = FMD.TIMER.delay(() -> {
				try {
					block:
					if (!isDirty(this)) {
						for (Project p : dependencies) {
							if (isDirty(p)) break block;
						}
						return;
					}

					FMD.build(Collections.emptySet(), this);
				} catch (FastFailException ignored) {
					fileChanged();
				} catch (Throwable e) {
					Terminal.error("自动编译出错", e);
					FMD.watcher.removeAll();
				}
			}, FMD.config.getInt("自动编译防抖"));
		}
	}
	private static boolean isDirty(Project p) {
		var modified = FMD.watcher.getModified(p, IFileWatcher.ID_SRC);
		if (modified.contains(null) || modified.isEmpty()) {
			if (modified.contains(null)) FMD.LOGGER.debug("{}未注册监听器", p.getName());
			return false;
		}
		return true;
	}

	public FMD.EnvPojo.Project serialize() {return conf;}
	public Map<String, String> getVariables() {return variables;}
	public String getOutputFormat() {return Formatter.simple(conf.name_format).format(variables, IOUtil.getSharedCharBuf()).toString();}
}