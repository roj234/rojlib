package roj.ci;

import roj.archive.ArchiveUtils;
import roj.archive.zip.ZipOutput;
import roj.asmx.mapper.Mapper;
import roj.ci.plugin.ProcessEnvironment;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.TrieTreeSet;
import roj.concurrent.TimerTask;
import roj.io.IOUtil;
import roj.text.Formatter;
import roj.ui.Tty;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.FastFailException;
import roj.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
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

	Env.Project conf;

	private final String name;
	String version;
	final Charset charset;
	public final File root, srcPath, resPath, libPath, cachePath;

	private List<Project> initialDep, buildOrderDep;
	private EnumMap<Dependency.Scope, List<Dependency>> dependencies;
	private List<Dependency> compileDependencies;

	final Compiler compiler;
	public final Workspace workspace;
	public final Map<String, String> variables;
	private final TrieTreeSet varReplacePathPrefix;

	final File unmappedJar;
	private final int resPrefix;
	ZipOutput mappedWriter, unmappedWriter;
	final DependencyGraph dependencyGraph = new DependencyGraph();

	FMD.SignatureCache signatureCache;

	public ProcessEnvironment.CacheState cacheState;

	@Deprecated public Mapper mapper;
	@Deprecated public Mapper.State mapperState;

	public String getName() {return conf.name;}
	public String getSafeName() {return cachePath.getName();}
	public String getShortName() {return name;}
	public int getResPrefix() {return resPrefix;}

	public Project(Env.Project config, boolean mkdirs) throws IOException {
		this.conf = config;
		this.name = config.name.substring(config.name.lastIndexOf('/')+1);
		this.version = config.version.toString();
		this.charset = config.charset;

		var ws = FMD.workspaces.get(config.workspace);
		if (ws == null) throw new NullPointerException("找不到工作空间"+config.workspace);
		this.workspace = ws;

		this.root = new File(workspace.getProjectPath(), config.name);
		this.srcPath = new File(root, "java");
		this.resPath = new File(root, "resources");
		this.libPath = new File(root, "lib");
		this.cachePath = new File(FMD.CACHE_PATH, config.name.equals(name) ? name : name+"@"+Integer.toHexString(config.name.hashCode()));
		this.unmappedJar = new File(cachePath, "classes.jar");

		this.variables = new HashMap<>(ws.variables);
		this.variables.put("project_name", name);
		this.variables.put("project_version", version);
		this.variables.putAll(config.variables);
		/*for (var itr = this.variables.keySet().iterator(); itr.hasNext(); ) {
			if (itr.next().startsWith("fmd:")) itr.remove();
		}*/

		this.dependencies = new EnumMap<>(Dependency.Scope.class);
		if (workspace.variable_replace_in.isEmpty() || conf.variable_replace_in.isEmpty()) {
			this.varReplacePathPrefix = workspace.variable_replace_in.isEmpty() ? conf.variable_replace_in : workspace.variable_replace_in;
		} else {
			this.varReplacePathPrefix = new TrieTreeSet(conf.variable_replace_in);
			this.varReplacePathPrefix.addAll(workspace.variable_replace_in);
		}

		if (config.type == Env.Type.ARTIFACT) {
			resPrefix = 0;
			compiler = null;
			return;
		}

		String compilerType = config.variables.getOrDefault("fmd:compiler", "Javac");
		var obj = FMD.compilerTypes.get(compilerType);
		if (obj == null) throw new NullPointerException("找不到编译器"+compilerType);

		compiler = obj.getInstance(srcPath.getAbsolutePath().replace(File.separatorChar, '/'));

		if (root.mkdirs() || mkdirs) {
			srcPath.mkdir();
			resPath.mkdir();
			libPath.mkdir();
		}
		cachePath.mkdir();
		unmappedJar.createNewFile();

		unmappedWriter = new ZipOutput(unmappedJar);
		unmappedWriter.setCompress(true);

		resPrefix = resPath.getAbsolutePath().length()+1;
	}
	public void init() {
		if (compileDependencies != null) return;
		compileDependencies = new ArrayList<>();
		initialDep = new ArrayList<>();

		for (var entry : conf.dependency.entrySet()) {
			var id = entry.getKey();
			Dependency dep;
			if (id.startsWith("FILE:")) {
				File file = IOUtil.relativePath(root, id.substring(5));
				if (!file.isFile()) {
					FMD.LOGGER.warn("找不到依赖项目{}", id);
					continue;
				}
				dep = new Dependency.FileDep(file);
			} else if (id.startsWith("DIR:")) {
				File dir = IOUtil.relativePath(root, id.substring(4));
				if (!dir.isDirectory()) {
					FMD.LOGGER.warn("找不到依赖项目{}", id);
					continue;
				}
				dep = new Dependency.DirDep(dir);
			} else {
				Project project = FMD.projects.get(id);
				if (project == null) {
					FMD.LOGGER.warn("找不到依赖项目{}", id);
					continue;
				}
				dep = new Dependency.ProjectDep(project);
				initialDep.add(project);
			}

			dependencies.computeIfAbsent(entry.getValue(), Helpers.fnArrayList()).add(dep);
			if (entry.getValue().inClasspath()) compileDependencies.add(dep);
			if (entry.getValue().copyResource()) {
				if (!conf.type.canBuild()) throw new IllegalArgumentException("不可构建的子模块不允许使用BUNDLED或PROCESSED引用\n如果子模块被BUNDLED，那么其EXPORT类型将被一并合入");
			}
		}

		var projects = new LinkedHashSet<Project>();
		gatherDependencies(projects, this);
		buildOrderDep = new ArrayList<>(projects);
	}

	@Override
	public String toString() {return getName();}

	private void gatherDependencies(Set<Project> all, Project exportOwner) {
		for (var project : initialDep) {
			project.init();
			project.gatherDependencies(all, exportOwner);

			// EXPORT类型的依赖具有传递性
			List<Dependency> exports = project.dependencies.getOrDefault(Dependency.Scope.EXPORT, Collections.emptyList());
			for (Dependency dependency : exports) {
				if (!exportOwner.compileDependencies.contains(dependency)) exportOwner.compileDependencies.add(dependency);

				List<Dependency> bundleDeps = dependencies.getOrDefault(Dependency.Scope.BUNDLED, Collections.emptyList());
				boolean isBundled = bundleDeps.contains(new Dependency.ProjectDep(project));
				if (isBundled && dependency.project().variables.getOrDefault("fmd:exportBundle", "true").equals("true")) {
					if (!bundleDeps.contains(dependency)) bundleDeps.add(dependency);
				}
			}
			all.add(project);
		}
	}

	public List<Project> getProjectDependencies() {return buildOrderDep;}
	public List<Dependency> getCompileDependencies() {return compileDependencies;}

	public void getDependencyClasses(ProcessEnvironment context, long stamp) throws IOException {
		for (Dependency dependency : dependencies.getOrDefault(Dependency.Scope.BUNDLED, Collections.emptyList())) {
			dependency.getClasses(context, stamp);
		}
	}

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
							for (String fileName : set) {
								File file = new File(fileName);
								if (file.isFile()) writeRes(file, useCompileTimestamp != 0 ? useCompileTimestamp : file.lastModified(), Collections.emptyMap());
								else mappedWriter.set(fileName.substring(resPrefix).replace(File.separatorChar, '/'), (ByteList) null);
							}
							set.clear();
						}
						break block;
					}
				}

				// update all resource ??
				IOUtil.listFiles(resPath, file -> {
					long lastModified = file.lastModified();
					if (lastModified >= stamp) writeRes(file, useCompileTimestamp != 0 ? useCompileTimestamp : lastModified, Collections.emptyMap());
					return false;
				});
			}

			List<Dependency> bundles = dependencies.getOrDefault(Dependency.Scope.BUNDLED, Collections.emptyList());
			for (Dependency bundle : bundles) {
				resCount += bundle.getResources(this, mappedWriter, stamp);
			}

			mappedWriter.setCompress(prevCompress);
			return resCount;
		};
	}
	void writeRes(File file, long time, Map<String, String> altVariables) {
		resCount++;
		String relPath = file.getAbsolutePath().substring(resPrefix).replace(File.separatorChar, '/');
		mappedWriter.setCompress(!ArchiveUtils.INCOMPRESSIBLE_FILE_EXT.contains(IOUtil.extensionName(relPath)));
		try {
			if (varReplacePathPrefix.strStartsWithThis(relPath)) {
				String string;
				try {
					string = IOUtil.readString(file);
				} catch (Exception e) {
					FMD.LOGGER.warn("变量替换规则可能命中了二进制文件{}", e, relPath);
					return;
				}

				var template = Formatter.simple(string);
				if (template.isDynamic()) {
					string = template.format(new TwoMap(altVariables), IOUtil.getSharedCharBuf()).toString();
				}

				String fuckJavac = string;
				mappedWriter.set(relPath, () -> DynByteBuf.wrap(fuckJavac.getBytes(charset)), time);
				return;
			}

			mappedWriter.setStream(relPath, () -> new FileInputStream(file), time);
		} catch (IOException e) {
			FMD.LOGGER.warn("资源文件{}复制失败", e, relPath);
		}
	}

	private TimerTask delayedCompile;
	private boolean autoCompile;
	public void compileSuccess(boolean increment) {
		if (delayedCompile != null) delayedCompile.cancel();
		try {
			FMD.watcher.add(this);
		} catch (IOException e) {
			Tty.warning("无法启动文件监控", e);
		}
		if (!increment) {
			var copyTo = variables.get("fmd_x:copy_to");
			if (copyTo != null) {
				try {
					IOUtil.copyFile(mappedWriter.file, new File(copyTo));
					Tty.success("已将编译产物["+name+"]复制到目标文件夹");
				} catch (IOException e) {
					Tty.warning("无法复制文件", e);
				}
			}
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
						for (Project p : buildOrderDep) {
							if (isDirty(p)) break block;
						}
						return;
					}

					FMD.build(new HashSet<>("auto"), this);
				} catch (FastFailException ignored) {
					fileChanged();
				} catch (Throwable e) {
					Tty.error("自动编译出错", e);
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

	public Env.Project serialize() {return conf;}
	public Map<String, String> getVariables() {return variables;}
	public String getOutputFormat() {return Formatter.simple(variables.getOrDefault("fmd:name_format", "${project_name}-${project_version}.jar")).format(variables, IOUtil.getSharedCharBuf()).toString();}

	public final class TwoMap extends AbstractMap<String, Object> {
		private final Map<String, String> altVariables;
		public TwoMap(Map<String, String> altVariables) {this.altVariables = altVariables;}

		public Set<Entry<String, Object>> entrySet() {return Collections.emptySet();}
		public Object get(Object key) {return altVariables.getOrDefault(key, variables.get(key));}
	}
}