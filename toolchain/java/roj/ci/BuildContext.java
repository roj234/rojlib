package roj.ci;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import roj.annotation.MayMutate;
import roj.archive.zip.ZipEditor;
import roj.archive.zip.ZipEntry;
import roj.asm.ClassNode;
import roj.asm.attr.Annotations;
import roj.asmx.AnnotatedElement;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.Context;
import roj.asmx.TransformException;
import roj.asmx.injector.CodeWeaver;
import roj.asmx.injector.WeaveException;
import roj.asmx.mapper.Mapper;
import roj.ci.annotation.IndirectReference;
import roj.ci.plugin.Plugin;
import roj.collect.*;
import roj.gui.Profiler;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.TypedKey;
import roj.util.function.ExceptionalSupplier;
import roj.util.function.Flow;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static roj.ci.MCMake.log;

/**
 * Manages the build context for a project, handling incremental compilation, resource tracking,
 * class transformations, and plugin integration. This class coordinates the build process,
 * including changesets for sources and resources, processor hooks, and output generation.
 *
 * @author Roj234
 * @since 3.6
 */
public final class BuildContext {
	static final FindSet<Dependency> ALL_DEPENDENCIES = new HashSet<>();
	public static Dependency internDependency(Dependency dep) {
		synchronized (ALL_DEPENDENCIES) {
			return ALL_DEPENDENCIES.intern(dep);
		}
	}
	public static void closeAllDependencies() {
		synchronized (ALL_DEPENDENCIES) {
			for (Dependency dependency : ALL_DEPENDENCIES) {
				IOUtil.closeSilently(dependency);
			}
		}
		ALL_DEPENDENCIES.clear();
	}

	static final Set<Dependency> ALL_OPENED_DEPENDENCIES = Collections.newSetFromMap(new ConcurrentHashMap<>());

	void openDependencies() throws IOException {
		project.initializeFileList(incrementLevel == INC_FULL);
		for (Dependency dep : project.getCompileDependencies()) {
			if (ALL_OPENED_DEPENDENCIES.add(dep)) {
				dep.open(this);
			}
		}
	}
	static void cleanup() {
		for (Dependency dep : ALL_OPENED_DEPENDENCIES) {
			dep.unlock();
		}
		ALL_OPENED_DEPENDENCIES.clear();
	}

	/**
	 * The project being built.
	 */
	public final Project project;
	/**
	 * The start time of the build process, in milliseconds since the epoch.
	 * Used for timestamp-based comparisons during incremental builds.
	 */
	public final long buildStartTime = System.currentTimeMillis();
	/**
	 * The timestamp of the last successful build, in milliseconds since the epoch.
	 * Used to detect file changes in incremental builds.
	 */
	public long lastBuildTime;
	/**
	 * 这个构建失败了
	 * @since 3.17
	 */
	public boolean hasError;

	/**
	 * Indicates a full build, scanning all files without relying on prior state.
	 */
	public static final int INC_FULL = 0,
	/**
	 * Indicates a rebuild triggered by a missing artifact, recreating from cache if available.
	 */
	INC_REBUILD = 1,
	/**
	 * Indicates a cold start, where no prior state in memory.
	 */
	INC_LOAD = 2,
	/**
	 * Indicates a regular incremental build, updating only changed files.
	 */
	INC_UPDATE = 3;

	int incrementLevel;

	/**
	 * The type of incremental build to perform. Must be one of {@link #INC_FULL},
	 * {@link #INC_REBUILD}, {@link #INC_LOAD}, or {@link #INC_UPDATE}.
	 * This value may be adjusted by processors during the build.
	 *
	 * @see #INC_FULL
	 * @see #INC_REBUILD
	 * @see #INC_LOAD
	 * @see #INC_UPDATE
	 * @since 3.8
	 */
	@MagicConstant(intValues = {INC_FULL, INC_REBUILD, INC_LOAD, INC_UPDATE})
	public int getIncrementLevel() {return incrementLevel;}

	/**
	 * The list of registered processors for this build, loaded from the workspace.
	 * Processors can hook into various stages of the build process.
	 */
	private final List<Plugin> plugins;

	/**
	 * Constructs a new build context for the given project and incremental build type.
	 * Initializes changesets for resources and sources, and prepares persistent state if needed.
	 * Sets the project as currently compiling with this context.
	 *
	 * @param p the project to build
	 * @param incrementLevel the initial incremental build type
	 */
	public BuildContext(Project p, int incrementLevel) {
		this.project = p;
		this.lastBuildTime = p.unmappedJar.lastModified();

		this.plugins = p.workspace.getPlugins();
		this.resources = new Changeset(project.resPath, project.getResPrefix(), FileWatcher.ID_RES, -1);
		this.sources = new JavaChangeset(project.srcPath);

		if (incrementLevel == INC_FULL) {
			project.persistentState = new PersistentState();
			project.clearPersistentState();
		} else if (project.persistentState == null) {
			incrementLevel = Math.min(incrementLevel, INC_LOAD);
			project.persistentState = new PersistentState();
		}
		this.incrementLevel = incrementLevel;

		p.compiling = this;
	}

	//region Changeset
	/**
	 * The changeset tracking changes to resource files.
	 * Resources include non-Java assets like configurations or textures.
	 */
	public final Changeset resources;
	/**
	 * The changeset tracking changes to Java source files.
	 * Handles incremental updates, dependency propagation, and structure diffs.
	 */
	public final JavaChangeset sources;
	//public final Changeset classes;

	private Changeset fullResources;
	/**
	 * Returns the changeset tracking all resources, creating it on demand if needed.
	 * Intended for use in {@link #INC_REBUILD} scenarios or when full resource scanning is required.
	 *
	 * @return the full resources changeset
	 */
	public Changeset getFullResources() {
		if (fullResources == null) {
			fullResources = new Changeset(project.resPath, project.getResPrefix(), FileWatcher.ID_RES, INC_REBUILD);
		}
		return fullResources;
	}

	/**
	 * Structure repository diff handles, for inner classes diff check.
	 *
	 * @since 3.8
	 */
	Map<String, Object> structureDiffHandles = Collections.emptyMap();

	/**
	 * A sealed interface representing a changeset of files, either resources or sources.
	 * Tracks changed and removed files based on the current build {@link #incrementLevel} level.
	 * Changes are detected via file watchers for incremental builds or full scans otherwise.
	 */
	public sealed class Changeset {
		final File basePath;
		final int prefixLength;
		final int watcherSlot;
		private final int overrideIncrement;
		List<File> changed;
		Set<String> removed = Collections.emptySet();

		Changeset(File path, int prefix, int watcherSlot, int overrideIncrement) {
			this.basePath = path;
			this.prefixLength = prefix;
			this.watcherSlot = watcherSlot;
			this.overrideIncrement = overrideIncrement;
		}

		/**
		 * Returns the list of changed files in this changeset.
		 * For incremental builds ({@link #incrementLevel} > {@link #INC_REBUILD}), uses file watcher data if available;
		 * otherwise, performs a full scan of the base path, filtering by last modified time against {@link #lastBuildTime}.
		 *
		 * @return the list of changed files
		 * @throws IOException if an I/O error occurs while listing or accessing files
		 * @implNote Must call this before call getRemoved()
		 */
		@UnmodifiableView
		public List<File> getChanged() throws IOException {
			if (changed == null) {
				int incr = overrideIncrement < 0 ? incrementLevel : overrideIncrement;
				if (incr > INC_REBUILD) {
					removed = new HashSet<>();

					Set<String> modified = MCMake.watcher.getModified(project, watcherSlot);
					if (!modified.contains(null)) {
						changed = new ArrayList<>();

						synchronized (modified) {
							for (String pathname : modified) {
								File file = new File(pathname);
								if (file.isFile()) changed.add(file);
								else removed.add(pathname.substring(prefixLength).replace(File.separatorChar, '/'));
							}
						}
						return changed;
					}

					for (String pathname : project.getDeleted(watcherSlot)) {
						removed.add(pathname.substring(prefixLength).replace(File.separatorChar, '/'));
					}
				}

				changed = IOUtil.listFiles(basePath, (pathname, attr) -> incr <= INC_REBUILD || attr.lastModifiedTime().toMillis() >= lastBuildTime);
			}
			return changed;
		}

		/**
		 * Returns the set of removed file paths, relative to the prefix.
		 * Paths are normalized to use forward slashes ('/').
		 *
		 * @return the set of removed files (unmodifiable, empty if none)
		 */
		@UnmodifiableView
		public Set<String> getRemoved() {return removed;}

		public boolean isEmpty() {return (changed.size()|removed.size()) == 0;}
	}
	public final class JavaChangeset extends Changeset {
		Set<String> changedClasses;
		List<File> changeDeps = Collections.emptyList();
		Set<String> structureChanged = Collections.emptySet();

		JavaChangeset(File path) {super(path, -1, FileWatcher.ID_SRC, -1);}

		@Override
		public List<File> getChanged() throws IOException {
			if (changed != null) return changed;
			changedClasses = new HashSet<>();

			Project p = project;

			loadDep:
			if (incrementLevel != INC_FULL) {
				if (p.dependencyGraph.isEmpty()) {
					Profiler.startSection("memoryCacheConstruct");
					ZipEditor za;
					try {
						za = p.unmappedWriter.getArchive();
					} catch (Exception e) {
						log.error("Failed to construct memory cache; force full build", e);

						p.unmappedWriter.begin(false);
						p.unmappedWriter.end();

						incrementLevel = INC_FULL;
						break loadDep;
					}
					for (ZipEntry ze : za.entries()) {
						ClassNode node = ClassNode.parseSkeleton(za.get(ze));
						p.dependencyGraph.add(node);
						p.structureRepo.add(node);
					}
					p.annotationRepo.loadCacheOrAdd(za);
					p.unmappedWriter.close();
					if (!p.dependencyGraph.isEmpty()) {
						log.debug("Constructed memory cache from file.");
					}
					Profiler.endSection();
				}
			}

			useFastpath: {
				if (incrementLevel > INC_REBUILD) {
					removed = new HashSet<>();
					changeDeps = new ArrayList<>();

					Set<String> modified = MCMake.watcher.getModified(project, watcherSlot);
					if (!modified.contains(null)) {
						changed = new ArrayList<>();

						synchronized (modified) {
							for (String pathname : modified) {
								incrementChange(pathname);
							}
						}

						break useFastpath;
					}

					for (String pathname : project.getDeleted(watcherSlot)) {
						incrementChange(pathname);
					}
				}

				if (incrementLevel == INC_FULL) {
					p.dependencyGraph.clear();
					p.annotationRepo.getAnnotations().clear();
					p.structureRepo.clear();

					changed = IOUtil.listFiles(basePath, JavaChangeset::isCompilable);
				} else {
					changed = new ArrayList<>();
					changeDeps = new ArrayList<>();
					IOUtil.listPaths(basePath, (pathname, attr) -> {
						boolean chosen = attr.lastModifiedTime().toMillis() >= lastBuildTime && isCompilable(pathname, attr);
						if (chosen) incrementChange(pathname);
					});
				}
			}

			structureChanged = new HashSet<>(changed.size());
			String srcPrefix = project.srcPath.getPath();
			for (File file : changed) {
				var pathname = file.getPath();
				var className = pathname.substring(srcPrefix.length() + 1, pathname.length() - 5).replace(File.separatorChar, '/');
				structureChanged.add(className);
			}

			if (incrementLevel != INC_FULL) {
				for (var project : p.getProjectDependencies()) {
					BuildContext state = project.compiling;
					if (state != null) {
						var directChanges = state.sources.structureChanged;
						if (!directChanges.isEmpty()) {
							log.trace("DepGraph propagate[{}]", project.getName());
							for (var className : directChanges) {
								addDependency(className, srcPrefix);
							}
						}
					}
				}
			}

			changed.addAll(changeDeps);
			changeDeps = Collections.emptyList();

			p.dependencyGraph.remove(changedClasses);
			p.annotationRepo.remove(changedClasses);
			structureDiffHandles = p.structureRepo.applyDiff(changedClasses);

			if (changedClasses.size() > 0)
				log.debug("Evicted {} from memory cache.", changedClasses.size());
			if (structureDiffHandles.size() > 0)
				log.debug("Got {} structure diff handles.", structureDiffHandles.size());
			return changed;
		}

		static boolean isCompilable(String path, BasicFileAttributes attr) {
			return IOUtil.getExtension(path).equals("java");
		}

		private void incrementChange(String pathname) {
			String srcPrefix = project.srcPath.getPath();
			var className = pathname.substring(srcPrefix.length() + 1, pathname.length() - 5).replace(File.separatorChar, '/');

			var file = new File(pathname);
			if (file.isFile()) changed.add(file);
			else {
				removed.add(className);
				project.structureRepo.fileRemoved(className);
			}
			changedClasses.add(className);

			addDependency(className, srcPrefix);
		}

		private void addDependency(String className, String srcPrefix) {
			log.trace(" Query: {}", className);
			for (String reference : project.dependencyGraph.get(className)) {
				var path = srcPrefix + File.separatorChar + reference + ".java";
				File ref = new File(path);
				if (ref.isFile()) {
					log.trace("  Reference: {}", reference);
					if (changedClasses.add(reference)) {
						changeDeps.add(ref);
					}
				}
			}
		}
	}
	//endregion
	//region Hooks (sorted by calling order)
	/**
	 * Wraps a resource input stream through all registered processors.
	 *
	 * @param path the resource path
	 * @param in the input stream to wrap
	 * @return the wrapped input stream
	 */
	InputStream wrapResource(String path, InputStream in) {
		for (int i = 0; i < plugins.size(); i++) {
			in = plugins.get(i).wrapResource(path, in);
		}
		return in;
	}

	/**
	 * Invokes the beforeCompile hook on all processors, potentially adjusting the increment level.
	 *
	 * @param options the compiler options
	 * @param sources the list of source files
	 * @return the adjusted increment level
	 */
	int beforeCompile(ArrayList<String> options, List<File> sources) {
		for (int i = 0; i < plugins.size(); i++) {
			Profiler.startSection("plugin["+plugins.get(i).name()+"]");
			if (plugins.get(i).preProcess(options, sources, this)) {
				incrementLevel = Math.min(INC_LOAD, incrementLevel);
			}
			Profiler.endSection();
		}
		return incrementLevel;
	}

	private List<Context> changedClasses;
	private int directChangedClasses;
	private final Set<String> removedClasses = new HashSet<>();

	// 这三个结构节约了内存，不过若是对性能有很大影响，考虑使用XashMap<Context, Record(boolean isChanged, Project owner)>
	// 考虑到都是KB级别的，而且多半还是young gen，也许我没必要这么抠
	static final class ExtClass {
		Context classInfo;
		Project owner;
		boolean isChanged;

		@IndirectReference
		private ExtClass _next;

		public ExtClass(Context context, Project project, boolean needWriteIfOnLoad) {
			this.classInfo = context;
			this.owner = project;
			this.isChanged = needWriteIfOnLoad;
		}
	}
	private static final XashMap.Template<Context, ExtClass> EXTERNAL_CLASS_INFO_TEMPLATE = XashMap.forType(Context.class, ExtClass.class).key("classInfo").hasher(Hasher.identity()).build();
	private final XashMap<Context, ExtClass> externalClasses = EXTERNAL_CLASS_INFO_TEMPLATE.create();

	/**
	 * Set directly (a.k.a. not from dependencies) changed classes.
	 *
	 * @param changedClasses the list of class contexts
	 * @see MCMake#compile(Set, Project, BuildContext)
	 */
	void setChangedClasses(List<Context> changedClasses) {
		this.changedClasses = changedClasses;
		this.directChangedClasses = changedClasses.size();
	}

	/**
	 * @param fileName 格式: 文件名
	 */
	void classRemoved(String fileName) {removedClasses.add(fileName);}

	/**
	 * Adds a class context to the build, associating it with a project and optionally mark it as changed.
	 *
	 * @param context the class context
	 * @param project the owning project (or null for the main project)
	 * @param needWriteIfOnLoad whether the class is needed to write.
	 * @see Dependency#getClasses(BuildContext)
	 */
	void addClass(Context context, @Nullable Project project, boolean needWriteIfOnLoad) {
		if (project == null) changedClasses.add(context);
		else {
			externalClasses.add(new ExtClass(context, project, needWriteIfOnLoad && incrementLevel == INC_LOAD));
		}
	}

	/**
	 * Performs post-compilation processing: partitions classes by project, invokes processor afterCompile hooks,
	 * runs transformers, and prepares changed classes for output.
	 *
	 * @return the final list of processed classes, or null if transformation failed
	 */
	List<Context> afterCompile() {
		Profiler.startSection("partition");
		var classes = changedClasses;

		for (ExtClass info : externalClasses) {
			classes.add(info.classInfo);
		}

		Profiler.endStartSection("hook.pre");
		for (int i = 0; i < plugins.size(); i++) {
			Profiler.startSection("plugin["+plugins.get(i).name()+"]");
			plugins.get(i).process(this);
			Profiler.endSection();
		}

		Profiler.endStartSection("transform");
		try {
			project.persistentState.runTransformers(classes);
		} catch (TransformException e) {
			log.error("Exception transforming classes", e);
		}

		if (incrementLevel == INC_LOAD) {
			ArrayList<Context> classes1 = classes instanceof ArrayList<Context> x ? x : new ArrayList<>(classes);

			int originalSize = classes1.size();
			classes1._setSize(directChangedClasses);

			for (var info : externalClasses) {
				if (info.isChanged) {
					classes1.add(info.classInfo);
				}
			}

			Object[] a = classes1.getInternalArray();
			for (int i = classes1.size(); i < originalSize; i++) a[i] = null;

			changedClasses = classes = classes1;
		}
		byAnnotation = null;

		Profiler.endStartSection("hook.post");
		for (int i = 0; i < plugins.size(); i++) {
			Profiler.startSection("plugin["+plugins.get(i).name()+"]");
			plugins.get(i).postProcess(this);
			Profiler.endSection();
		}

		Profiler.endSection();
		return classes;
	}

	/**
	 * Writes all generated files to the output archive.
	 *
	 * @param writer the zip output to write to
	 * @return the number of files written
	 * @throws IOException if an I/O error occurs during writing
	 * @see MCMake#build(Set, Project, File)
	 */
	@SuppressWarnings("unchecked")
	int writeGenerated(ZipOutput writer) throws IOException {
		for (var entry : generatedFiles.entrySet()) {
			if (entry.getValue() instanceof ByteList b) {
				writer.set(entry.getKey(), b);
			} else {
				writer.set(entry.getKey(), (ExceptionalSupplier<DynByteBuf, IOException>) entry.getValue(), buildStartTime);
			}
		}
		log.debug("Write {} generated files for {}", generatedFiles.size(), project.getName());
		if (generatedFiles.size() > 0) log.trace("{}", generatedFiles.keySet());
		return generatedFiles.size();
	}

	void buildSuccess() {
		Set<String> modified = MCMake.watcher.getModified(project, IFileWatcher.ID_RES);
		if (!modified.contains(null)) modified.clear();
		modified = MCMake.watcher.getModified(project, IFileWatcher.ID_SRC);
		if (!modified.contains(null)) modified.clear();
	}
	//endregion

	/**
	 * A static class holding persistent state for the build, including transformers,
	 * hooks, and mappers for class processing.
	 */
	public static final class PersistentState {
		private final Map<TypedKey<?>, Object> data = new HashMap<>();

		private final CodeWeaver weaver = new CodeWeaver();
		private final ConstantPoolHooks hooks = new ConstantPoolHooks();
		private final Mapper mapper = new Mapper();

		void runTransformers(List<Context> classes) throws TransformException {
			if (!weaver.registry().isEmpty()) {
				for (int i = 0; i < classes.size(); i++) {
					Context context = classes.get(i);
					weaver.transform(context.getClassName(), context);
				}
			}
			if (hooks.isNotEmpty()) {
				for (int i = 0; i < classes.size(); i++) {
					Context context = classes.get(i);
					hooks.transform(context.getClassName(), context);
				}
			}
			if (!mapper.getClassMap().isEmpty()) {
				mapper.setupHierarchy(0);
				// 说真的，我不喜欢后处理
				for (int i = 0; i < classes.size(); i++) {
					Context context = classes.get(i);
					mapper.S5_mapClassName(context);
					mapper.S5_1_resetDebugInfo(context);
				}
			}
		}

		/**
		 * Returns the mapper instance for class name remapping and debug info adjustments.
		 *
		 * @return the mapper
		 */
		public Mapper getMapper() {return mapper;}
	}

	/**
	 * Returns an unmodifiable view of the set of class names whose structure (fields, methods, interfaces,
	 * superclasses, etc.) has changed. Based on source file changes and dependency propagation.
	 *
	 * @return the set of structure-changed [PrefixClassName] (empty if none)
	 * @since 3.8
	 */
	@UnmodifiableView
	public Set<String> getStructureChangedClassNames() {return sources.structureChanged;}
	/**
	 * Returns an unmodifiable view of the list of compiled and processed class contexts.
	 * Includes directly changed classes and those from dependencies.
	 *
	 * @return the list of changed classes (empty if none)
	 */
	@UnmodifiableView public List<Context> getChangedClasses() {return changedClasses;}

	/**
	 * Returns direct changed class count (compiled from current module, not inherited)
	 */
	public int getDirectChangedClasses() {return directChangedClasses;}

	/**
	 * Returns an unmodifiable view of the set of removed class file names.
	 *
	 * @return the set of removed classes (empty if none)
	 */
	@UnmodifiableView public Set<String> getRemovedClasses() {return removedClasses;}

	/**
	 * Checks if any classes have been changed or removed in this build.
	 *
	 * @return {@code true} if classes have changed or been removed
	 * @since 3.8
	 */
	public boolean classesHaveChanged() {return (changedClasses.size()|removedClasses.size()) != 0;}

	//region Plugin APIs
	/**
	 * Get artifact of current building project
	 * @since 3.12
	 */
	public ZipOutput artifact() {return project.mappedWriter;}

	/**
	 * @since 3.12
	 */
	@SuppressWarnings("unchecked")
	public <T> T getIncrementCache(TypedKey<T> key) {return (T) project.persistentState.data.get(key);}

	/**
	 * @since 3.12
	 */
	public <T> void setIncrementCache(TypedKey<T> key, T value) {project.persistentState.data.put(key, value);}

	private final Map<String, Object> generatedFiles = new HashMap<>();
	/**
	 * Adds a generated file to the context, which will be written to the output archive during the build.
	 *
	 * @param name the path/name of the generated file (relative to the artifact root)
	 * @param data the byte data of the file
	 */
	public void addFile(String name, ByteList data) {generatedFiles.put(name, data);}

	/**
	 * Retrieves the variables map for the project owning the given class context.
	 * Falls back to the main project's variables if no specific owner is found.
	 *
	 * @param context the class context
	 * @return the variables map for the owning project
	 */
	public Map<String, ?> getVariables(Context context) {
		ExtClass extClass = externalClasses.get(context);
		if (extClass == null) return project.variables;
		return project.new TwoMap(extClass.owner.variables);
	}

	private Map<String, List<Context>> byAnnotation;
	/**
	 * Returns the list of classes annotated with the given annotation type.
	 * Lazily builds an index of annotations from the changed classes if not already present.
	 * Supports both runtime-visible and runtime-invisible annotations.
	 *
	 * @param annotation the fully qualified annotation type (e.g., "org/example/Annotation")
	 * @return the list of matching class contexts (empty if none)
	 */
	public List<Context> getAnnotatedClasses(String annotation) {
		if (byAnnotation == null) {
			byAnnotation = new HashMap<>();
			for (int j = 0; j < changedClasses.size(); j++) {
				Context ctx = changedClasses.get(j);
				ClassNode data = ctx.getData();

				var list = Annotations.getAnnotations(data, data, false);
				for (int i = 0; i < list.size(); i++) {
					byAnnotation.computeIfAbsent(list.get(i).type(), Helpers.fnArrayList()).add(ctx);
				}

				list = Annotations.getAnnotations(data, data, true);
				for (int i = 0; i < list.size(); i++) {
					byAnnotation.computeIfAbsent(list.get(i).type(), Helpers.fnArrayList()).add(ctx);
				}
			}
		}
		return byAnnotation.getOrDefault(annotation, Collections.emptyList());
	}

	/**
	 * Consumes all elements (classes, methods, fields, etc.) annotated with the given type
	 * from compile-time dependencies and the project's annotation repository.
	 * Useful for analysis or injection based on dependency annotations.
	 *
	 * @param type the fully qualified annotation type
	 * @param consumer the consumer to accept each annotated element
	 */
	public void getDepAnnotations(String type, Consumer<AnnotatedElement> consumer) {consumeAnnotations(type, consumer, project.getCompileDependencies());}

	/**
	 * Consumes all elements annotated with the given type from dependencies that will be bundled
	 * into the final artifact. Excludes compile-only dependencies.
	 *
	 * @param type the fully qualified annotation type
	 * @param consumer the consumer to accept each annotated element
	 */
	public void getBundledAnnotations(String type, Consumer<AnnotatedElement> consumer) {consumeAnnotations(type, consumer, project.getBundledDependencies());}

	private void consumeAnnotations(String type, Consumer<AnnotatedElement> consumer, List<Dependency> bundledDependencies) {
		for (Dependency dependency : bundledDependencies) {
			Set<AnnotatedElement> annotatedElements;
			try {
				annotatedElements = dependency.getAnnotations(this).annotatedBy(type);
			} catch (Exception e) {
				log.warn("Could not get annotations for " + dependency, e);
				continue;
			}
			annotatedElements.forEach(consumer);
		}

		project.annotationRepo.annotatedBy(type).forEach(consumer);
	}

	/**
	 * Loads a mixin class node into the weaver registry for application during class transformations.
	 * Mixins allow injecting code into target classes.
	 *
	 * @param mixin the mixin class node
	 * @throws WeaveException if the mixin fails to load or validate
	 * @since 3.15
	 */
	public void addMixin(@MayMutate ClassNode mixin) throws WeaveException {project.persistentState.weaver.load(mixin);}
	/**
	 * Enables constant pool hooks for the build and returns the hooks instance.
	 * Hooks allow custom transformations on constant pool entries during processing.
	 * Must be called before transformations to take effect.
	 *
	 * @return the constant pool hooks instance
	 */
	public ConstantPoolHooks classNodeEvents() {return project.persistentState.hooks;}
	/**
	 * Returns the mapper instance for remapping class names, method signatures, and debug information.
	 * Commonly used for obfuscation, access widening, or name shortening.
	 *
	 * @return the mapper instance
	 */
	public Mapper getMapper() {return project.persistentState.mapper;}

	/**
	 * Removes a class context from the list of changed classes and adjusts internal indices and partitions.
	 * Used during post-processing to filter out unwanted classes.
	 *
	 * @param context the class context to remove
	 */
	public void removeClass(Context context) {
		externalClasses.removeKey(context);
		int i = changedClasses.indexOf(context);
		if (i < 0) {
			log.error("{} already removed", context);
			return;
		}
		changedClasses.remove(i);
		if (i < directChangedClasses) directChangedClasses--;
	}

	/**
	 * Retrieves the first processor of the specified type from the registered processors.
	 *
	 * @param <T> the processor type
	 * @param type the class of the processor to find
	 * @return the processor instance, or {@code null} if not found
	 */
	@SuppressWarnings("unchecked")
	public <T extends Plugin> @Nullable T getProcessor(Class<T> type) {
		return (T) Flow.of(plugins).filter(p -> p.getClass() == type).findFirst().orElse(null);}
	//endregion
}