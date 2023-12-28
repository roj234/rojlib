package roj.mod;

import roj.archive.ArchiveConstants;
import roj.archive.zip.ZipOutput;
import roj.asmx.mapper.Mapper;
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.task.AsyncTask;
import roj.config.FileConfig;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.data.CString;
import roj.dev.Compiler;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.CLIUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static roj.mod.Shared.*;

/**
 * @author Roj233
 * @since 2021/7/11 13:59
 */
public final class Project extends FileConfig {
	static final MyHashMap<String, Project> projects = new MyHashMap<>();
	static final Matcher matcher = Pattern.compile("^[a-z_][a-z0-9_]*$").matcher("");

	public static Project load(String name) {
		if (!matcher.reset(name).matches()) throw new IllegalArgumentException("名称必须为全小写,不能以数字开头,可以包含下划线 ^[a-z_][a-z0-9_]*$");
		Project project = projects.get(name);
		if (project == null) projects.put(name, project = new Project(name));
		else project.reload();
		return project;
	}

	final String name;
	String version, atName;
	Charset charset;
	List<Project> dependencies;
	final Compiler compiler;

	Mapper.State state;
	String atConfigPathStr;
	final File srcPath, resPath, binJar;
	private final int resPrefix;

	ZipOutput dstFile, binFile;
	List<ATDesc> atEntryCache = new SimpleList<>();

	private Project(String name) {
		super(new File(BASE, "config/"+name+".json"), false);
		this.name = name;

		resPath = new File(BASE, "projects/"+name+"/resources");
		srcPath = new File(BASE, "projects/"+name+"/java");
		binJar = new File(BASE, "bin/"+name+"-dev.jar");
		resPrefix = resPath.getAbsolutePath().length()+1;

		// noinspection all
		resPath.mkdirs();
		// noinspection all
		srcPath.mkdirs();
		// noinspection all
		binJar.getParentFile().mkdir();

		// 自动备份源码已删除

		Set<String> ignores = new MyHashSet<>();
		FMDMain.readTextList(ignores::add, "忽略的编译错误码");
		this.compiler = new Compiler(null, null, ignores, srcPath.getAbsolutePath().replace(File.separatorChar, '/'));

		try {
			if (binJar.length() == 0) if (!binJar.createNewFile() || !binJar.setLastModified(0)) CLIUtil.warning("无法初始化StampFileTime");
			this.binFile = new ZipOutput(binJar);
		} catch (Throwable e) {
			CLIUtil.warning("无法初始化StampFile, 请尝试重新启动FMD或删除 "+binJar.getAbsolutePath(), e);
			LockSupport.parkNanos(3_000_000_000L);
			System.exit(-2);
		}

		load();
	}

	public List<Project> getAllDependencies() {
		if (dependencies.isEmpty()) return dependencies;

		LinkedMyHashMap<Project, Void> projects = new LinkedMyHashMap<>();

		List<Project> dest = new ArrayList<>(this.dependencies);
		List<Project> dest2 = new ArrayList<>();
		while (!dest.isEmpty()) {
			for (int i = 0; i < dest.size(); i++) {
				projects.put(dest.get(i), null);
				dest2.addAll(dest.get(i).dependencies);
			}
			List<Project> tmp = dest;
			dest = dest2;
			dest2 = tmp;
			dest2.clear();
		}

		for (Map.Entry<Project, Void> entry : projects.entrySet()) {
			dest2.add(entry.getKey());
		}

		return dest2;
	}

	public String dependencyString() {
		CharList cl = new CharList();
		if (!dependencies.isEmpty()) {
			for (int i = 0; i < dependencies.size(); i++) {
				cl.append(dependencies.get(i).name).append('|');
			}
			cl.setLength(cl.length() - 1);
		}
		return cl.toString();
	}

	public void setDependencyString(String text) {
		List<String> depend = TextUtil.split(new ArrayList<>(), text, '|');
		for (int i = 0; i < depend.size(); i++) {
			dependencies.add(Project.load(depend.get(i)));
		}
	}

	protected void load(CMapping map) {
		version = map.putIfAbsent("version", "1.0.0");

		String cs = map.putIfAbsent("charset", "UTF-8");
		charset = StandardCharsets.UTF_8;
		try {
			charset = Charset.forName(cs);
		} catch (UnsupportedCharsetException e) {
			CLIUtil.warning(name+" 的字符集 "+cs+" 不存在, 使用默认的UTF-8");
		}

		String atName = this.atName = map.putIfAbsent("atConfig", "");
		atConfigPathStr = atName.length() > 0 ? resPath.getPath() + "/META-INF/"+atName+".cfg" : null;

		List<String> required = map.getOrCreateList("dependency").asStringList();
		if (!required.isEmpty()) {
			for (int i = 0; i < required.size(); i++) {
				File config = new File(BASE, "/config/"+required.get(i)+".json");
				if (!config.exists()) {
					CLIUtil.warning(name+" 的前置"+required.get(i)+"未找到");
				} else {
					required.set(i, Helpers.cast(load(required.get(i))));
				}
			}
			dependencies = Helpers.cast(required);
		} else {
			dependencies = Collections.emptyList();
		}
	}

	public AsyncTask<MyHashSet<String>> getResourceTask(boolean inc) {
		return new AsyncTask<MyHashSet<String>>() {
			@Override
			protected MyHashSet<String> invoke() {
				boolean prevCompress = dstFile.isCompress();
				block: {
					// 检测是否第一次运行 关机状态无法检测文件变化
					if (state != null && inc) {
						Set<String> set = watcher.getModified(Project.this, FileWatcher.ID_RES);
						if (!set.contains(null)) {
							synchronized (set) {
								for (String s : set) writeRes(s);
								set.clear();
							}
							break block;
						}
					}

					IOUtil.findAllFiles(resPath, file -> {
						writeRes(file.getAbsolutePath());
						return false;
					});
				}

				dstFile.setCompress(prevCompress);

				loadMapper();
				return null;
			}
		};
	}

	final void writeRes(String s) {
		String relPath = s.substring(resPrefix).replace('\\', '/');
		dstFile.setCompress(!ArchiveConstants.INCOMPRESSIBLE_FILE_EXT.contains(IOUtil.extensionName(relPath).toLowerCase()));
		try {
			dstFile.set(relPath, new FileInputStream(s));
		} catch (IOException e) {
			CLIUtil.warning("资源文件", e);
		}
	}

	public void registerWatcher() {
		try {
			Shared.watcher.register(this);
		} catch (IOException e) {
			CLIUtil.warning("无法启动文件监控", e);
		}
	}

	@Override
	protected void save(CMapping map) {
		map.put("charset", charset == null ? "UTF-8" : charset.name());
		map.put("version", version);
		map.put("atConfig", atName);

		CList list = new CList(dependencies.size());
		for (int i = 0; i < dependencies.size(); i++) {
			list.add(CString.valueOf(dependencies.get(i).name));
		}
		map.put("dependency", list);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Project project = (Project) o;
		return name.equals(project.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}