package roj.ci;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipOutput;
import roj.asm.ClassNode;
import roj.asm.attr.Annotations;
import roj.asmx.AnnotatedElement;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.Context;
import roj.asmx.TransformException;
import roj.asmx.injector.CodeWeaver;
import roj.asmx.injector.WeaveException;
import roj.asmx.mapper.Mapper;
import roj.ci.plugin.Processor;
import roj.collect.*;
import roj.gui.Profiler;
import roj.io.IOUtil;
import roj.ui.Tty;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.TypedKey;
import roj.util.function.ExceptionalSupplier;
import roj.util.function.Flow;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/1/19 8:29
 */
public class BuildContext {
	public final Project project;
	public final List<Processor> processors;

	/**
	 * The changeset tracking changes to resources.
	 */
	public final Changeset resources;

	/**
	 * The changeset tracking all resources.
	 */
	private Changeset fullResources;

	/**
	 * The changeset tracking changes to source files.
	 */
	public final Changeset sources;

	/**
	 * Full build, scanning all files.
	 */
	public static final int INC_FULL = 0;

	/**
	 * Artifact is missing, recreate from cache.
	 */
	public static final int INC_REBUILD = 1;

	/**
	 * Cold start.
	 */
	public static final int INC_LOAD = 2;

	/**
	 * Regular incremental build.
	 */
	public static final int INC_UPDATE = 3;

	/**
	 * The type of incremental build to perform. Must be one of {@link #INC_FULL},
	 * {@link #INC_REBUILD}, {@link #INC_LOAD}, or {@link #INC_UPDATE}.
	 *
	 * @see #INC_FULL
	 * @see #INC_REBUILD
	 * @see #INC_LOAD
	 * @see #INC_UPDATE
	 */
	@MagicConstant(intValues = {INC_FULL, INC_REBUILD, INC_LOAD, INC_UPDATE})
	public int increment;

	/**
	 * The index of the last fully compiled class in the classes list.
	 */
	public int compiledClassIndex;

	/**
	 * The count of updates performed during the build.
	 */
	public int updateCount;

	private final Map<String, Object> generatedFiles = new HashMap<>();

	private Map<String, List<Context>> byAnnotation;

	public BuildContext(Project p, int increment) {
		this.project = p;
		this.lastBuildTime = p.unmappedJar.lastModified();

		this.processors = p.workspace.getProcessors();
		this.resources = new Changeset(project.resPath, project.getResPrefix(), FileWatcher.ID_RES, -1);
		this.sources = new JavaChangeset(project.srcPath);

		if (increment == INC_FULL) {
			project.persistentState = new PersistentState();
		} else if (project.persistentState == null) {
			increment = Math.min(increment, INC_LOAD);
			project.persistentState = new PersistentState();
		}
		//noinspection MagicConstant
		this.increment = increment;

		p.compiling = this;
	}

	/**
	 * The start time of the build process, in milliseconds since epoch.
	 */
	public final long buildStartTime = System.currentTimeMillis();
	/**
	 * The timestamp of the last build, used to determine file changes.
	 */
	public long lastBuildTime;

	public Changeset getFullResources() {
		if (fullResources == null) {
			fullResources = new Changeset(project.resPath, project.getResPrefix(), FileWatcher.ID_RES, INC_REBUILD);
		}
		return fullResources;
	}

	/**
	 * A sealed interface representing a changeset of files, either resources or sources.
	 * Tracks changed and deleted files based on the build {@code increment}.
	 */
	public sealed class Changeset {
		final File basePath;
		final int prefixLength;
		final int watcherSlot;
		private final int overrideIncrement;
		List<File> changed;
		Set<String> deleted = Collections.emptySet();

		public Changeset(File path, int prefix, int watcherSlot, int overrideIncrement) {
			this.basePath = path;
			this.prefixLength = prefix;
			this.watcherSlot = watcherSlot;
			this.overrideIncrement = overrideIncrement;
		}

		/**
		 * Returns the list of changed files in this changeset.
		 * For incremental builds and if available, uses file watcher data; otherwise, scans the base path.
		 *
		 * @return the list of changed files
		 * @throws IOException if an I/O error occurs while listing files
		 */
		public List<File> getChanged() throws IOException {
			if (changed == null) {
				int incr = overrideIncrement < 0 ? increment : overrideIncrement;
				if (incr > INC_REBUILD) {
					Set<String> modified = MCMake.watcher.getModified(project, watcherSlot);
					if (!modified.contains(null)) {
						changed = new ArrayList<>();
						deleted = new HashSet<>();

						synchronized (modified) {
							for (String pathname : modified) {
								File file = new File(pathname);
								if (file.isFile()) changed.add(file);
								else deleted.add(pathname.substring(prefixLength).replace(File.separatorChar, '/'));
							}
							modified.clear();
						}
						return changed;
					}
				}

				changed = IOUtil.listFiles(basePath, file -> incr <= INC_REBUILD || file.lastModified() >= lastBuildTime);
			}
			return changed;
		}

		/**
		 * Returns the set of deleted file paths (relative to the prefix).
		 *
		 * @return the set of deleted files
		 */
		public Set<String> getDeleted() {return deleted;}
	}
	public final class JavaChangeset extends Changeset {
		Set<String> changedClasses;

		JavaChangeset(File path) {super(path, -1, FileWatcher.ID_SRC, -1);}

		@Override
		public List<File> getChanged() throws IOException {
			if (changed != null) return changed;
			changedClasses = new HashSet<>();

			Project p = project;

			if (increment != INC_FULL) {
				if (p.dependencyGraph.isEmpty()) {
					Profiler.startSection("dependencyGraph");
					ZipArchive za = p.unmappedWriter.getArchive();
					for (ZEntry ze : za.entries()) {
						p.dependencyGraph.add(ClassNode.parseSkeleton(za.get(ze)));
					}
					p.annotationRepo.loadCacheOrAdd(za);
					p.unmappedWriter.close();
					if (!p.dependencyGraph.isEmpty()) {
						MCMake.LOGGER.debug("Loaded DepGraph/Annotation from cache", p.unmappedJar);
					}
					Profiler.endSection();
				}

				String srcPrefix = project.srcPath.getPath();
				for (var project : p.getProjectDependencies()) {
					BuildContext state = project.compiling;
					if (state != null) {
						for (File file : state.sources.getChanged()) {
							var pathname = file.getPath();
							var className = pathname.substring(srcPrefix.length() + 1, pathname.length() - 5).replace(File.separatorChar, '/');
							addDependency(project, className, srcPrefix);
						}
					}
				}
			}

			useFastpath: {
				if (increment > INC_REBUILD) {
					Set<String> modified = MCMake.watcher.getModified(project, watcherSlot);
					if (!modified.contains(null)) {
						changed = new ArrayList<>();
						deleted = new HashSet<>();

						synchronized (modified) {
							for (String pathname : modified) {
								incrementChange(new File(pathname));
							}
							modified.clear();
						}

						break useFastpath;
					}
				}

				if (increment == INC_FULL) {
					p.dependencyGraph.clear();
					p.annotationRepo.getAnnotations().clear();

					changed = IOUtil.listFiles(basePath, file -> IOUtil.extensionName(file.getName()).equals("java"));
				} else {
					changed = new ArrayList<>();
					IOUtil.listFiles(basePath, file -> {
						boolean chosen = file.lastModified() >= lastBuildTime && IOUtil.extensionName(file.getName()).equals("java");
						if (chosen) incrementChange(file);
						return false;
					});
				}
			}

			p.dependencyGraph.remove(changedClasses);
			p.annotationRepo.remove(changedClasses);
			if (changedClasses.size() > 0)
				MCMake.LOGGER.debug("Remove changed classes from cache: {}", changedClasses);
			return changed;
		}

		private void incrementChange(File file) {
			String pathname = file.getAbsolutePath();
			String srcPrefix = project.srcPath.getPath();
			var className = pathname.substring(srcPrefix.length() + 1, pathname.length() - 5).replace(File.separatorChar, '/');
			addDependency(project, className, srcPrefix);

			if (file.isFile()) {
				changed.add(file);
			} else {
				deleted.add(className);
			}
			changedClasses.add(className);
		}

		private void addDependency(Project p, String className, String srcPrefix) {
			for (String referent : p.dependencyGraph.get(className)) {
				referent = srcPrefix + File.separatorChar + referent + ".java";
				File ref = new File(referent);
				MCMake.LOGGER.trace("DepGraph[{}] {} => {}", p.getName(), referent, className);
				if (ref.isFile()) changed.add(ref);
			}
		}
	}

	/**
	 * A static class holding persistent state for the build, including transformers,
	 * hooks, and mappers for class processing.
	 */
	public static final class PersistentState {
		private final Map<TypedKey<?>, Object> data = new HashMap<>();

		private final CodeWeaver weaver = new CodeWeaver();
		private final ConstantPoolHooks hooks = new ConstantPoolHooks();
		private final Mapper mapper = new Mapper();
		private boolean needTransform;

		void runTransformers(List<Context> classes) throws TransformException {
			if (!weaver.registry().isEmpty()) {
				for (int i = 0; i < classes.size(); i++) {
					Context context = classes.get(i);
					weaver.transform(context.getClassName(), context);
				}
			}
			if (needTransform) {
				for (int i = 0; i < classes.size(); i++) {
					Context context = classes.get(i);
					hooks.transform(context.getClassName(), context);
				}
			}
			if (!mapper.getClassMap().isEmpty()) {
				mapper.initSelf(0);
				// 说真的，我不喜欢后处理
				for (int i = 0; i < classes.size(); i++) {
					Context context = classes.get(i);
					mapper.S5_mapClassName(context);
					mapper.S5_1_resetDebugInfo(context);
				}
			}
		}

		public Mapper getMapper() {return mapper;}
	}

	/**
	 * Wraps a resource input stream through all registered processors.
	 *
	 * @param path the resource path
	 * @param in the input stream to wrap
	 * @return the wrapped input stream
	 */
	public InputStream wrapResource(String path, InputStream in) {
		for (int i = 0; i < processors.size(); i++) {
			in = processors.get(i).wrapResource(path, in);
		}
		return in;
	}

	/**
	 * Invokes the beforeCompile hook on all processors, potentially adjusting the increment level.
	 *
	 * @param options the compiler options
	 * @param sources the list of source files
	 * @param increment the current increment level
	 * @return the adjusted increment level
	 */
	public int beforeCompile(ArrayList<String> options, List<File> sources, int increment) {
		for (int i = 0; i < processors.size(); i++) {
			increment = Math.min(processors.get(i).beforeCompile(options, sources, this), increment);
		}
		this.increment = increment;
		return increment;
	}

	private final IntervalPartition<IntervalPartition.Wrap<Project>> owners = new IntervalPartition<>();
	private final Map<Project, List<Context>> myClasses = new HashMap<>();
	private List<Context> classes;
	private final Set<Context> changed = new HashSet<>(Hasher.identity());
	/**
	 * Sets the list of compiled classes and initializes the compiled class index.
	 *
	 * @param classes the list of class contexts
	 */
	public void setClasses(List<Context> classes) {this.classes = classes;this.compiledClassIndex = classes.size();}
	/**
	 * Adds a class context to the build, associating it with a project and marking it as changed if writable.
	 *
	 * @param context the class context
	 * @param project the owning project (or null for the main project)
	 * @param writable whether the class is writable and should be tracked for changes
	 */
	public void addClass(Context context, Project project, boolean writable) {
		myClasses.computeIfAbsent(project, Helpers.fnArrayList()).add(context);
		if (writable && increment == INC_LOAD) changed.add(context);
	}

	/**
	 * Adds a generated file to the context for later writing.
	 *
	 * @param name the file name
	 * @param data the byte list data
	 */
	public void addFile(String name, ByteList data) {generatedFiles.put(name, data);}

	/**
	 * Performs post-compilation processing: partitions classes by project, invokes processor afterCompile hooks,
	 * runs transformers, and prepares changed classes for output.
	 *
	 * @return the final list of processed classes, or null if transformation failed
	 */
	public List<Context> afterCompile() {
		Profiler.startSection("partition");
		var classes = this.classes;
		classes.addAll(myClasses.getOrDefault(null, Collections.emptyList()));
		int index = classes.size();

		for (var entry : myClasses.entrySet()) {
			List<Context> value = entry.getValue();
			classes.addAll(value);
			owners.add(new IntervalPartition.Wrap<>(entry.getKey(), index, index += value.size()));
		}

		updateCount = increment == INC_LOAD ? compiledClassIndex : classes.size();

		Profiler.endStartSection("processor");
		for (int i = 0; i < processors.size(); i++) {
			processors.get(i).afterCompile(this);
		}
		this.classes = Collections.emptyList();

		Profiler.endStartSection("transformer");
		try {
			project.persistentState.runTransformers(classes);
		} catch (TransformException e) {
			Tty.error("类转换失败", e);
			return null;
		}

		for (Context ctx : classes) {
			if (changed.contains(ctx))
				generatedFiles.put(ctx.getFileName(), (ExceptionalSupplier<ByteList, IOException>) ctx::getCompressedShared);
		}

		updateCount = increment == INC_LOAD ? compiledClassIndex : classes.size();
		Profiler.endSection();
		return classes;
	}

	/**
	 * Writes all generated extra files to the mapped output archive.
	 *
	 * @param mappedWriter the zip output to write to
	 * @return the number of files written
	 * @throws IOException if an I/O error occurs during writing
	 */
	@SuppressWarnings("unchecked")
	public int writeExtra(ZipOutput mappedWriter) throws IOException {
		for (var entry : generatedFiles.entrySet()) {
			if (entry.getValue() instanceof ByteList b) {
				mappedWriter.set(entry.getKey(), b);
			} else {
				mappedWriter.set(entry.getKey(), (ExceptionalSupplier<ByteList, IOException>) entry.getValue(), buildStartTime);
			}
		}
		return generatedFiles.size();
	}

	/**
	 * Returns an unmodifiable view of the compiled classes list.
	 *
	 * @return the list of classes
	 */
	@UnmodifiableView
	public List<Context> getClasses() {return classes;}

	/**
	 * Retrieves the variables map for the project owning the given class context.
	 *
	 * @param context the class context
	 * @return the variables map, falling back to the main project if no specific owner
	 */
	public Map<String, ?> getVariables(Context context) {
		IntervalPartition.Segment segment = owners.segmentAt(classes.indexOf(context));
		if (segment == null) return project.variables;

		List<IntervalPartition.Wrap<Project>> coverage = segment.coverage();
		if (coverage.isEmpty()) return project.variables;

		return project.new TwoMap(coverage.get(0).obj.variables);
	}

	/**
	 * Returns the list of source classes annotated with the given annotation type.
	 * Builds an index of annotations if not already present.
	 *
	 * @param type the annotation type
	 * @return the list of matching class contexts
	 */
	public List<Context> getSourceAnnotations(String type) {
		if (byAnnotation == null) {
			byAnnotation = new HashMap<>();
			for (int j = 0; j < classes.size(); j++) {
				Context ctx = classes.get(j);
				ClassNode data = ctx.getData();

				var anns = Annotations.getAnnotations(data.cp, data, false);
				for (int i = 0; i < anns.size(); i++) {
					byAnnotation.computeIfAbsent(anns.get(i).type(), Helpers.fnArrayList()).add(ctx);
				}

				anns = Annotations.getAnnotations(data.cp, data, true);
				for (int i = 0; i < anns.size(); i++) {
					byAnnotation.computeIfAbsent(anns.get(i).type(), Helpers.fnArrayList()).add(ctx);
				}
			}
		}
		return byAnnotation.getOrDefault(type, Collections.emptyList());
	}

	/**
	 * Consumes all dependency elements annotated with the given type from compile dependencies and the project's annotation repo.
	 *
	 * @param type the annotation type
	 * @param consumer the consumer to accept annotated elements
	 */
	public void getDepAnnotations(String type, Consumer<AnnotatedElement> consumer) {consumeAnnotations(type, consumer, project.getCompileDependencies());}

	/**
	 * Consumes all dependency elements annotated with the given type that will be bundled to the artifact.
	 *
	 * @param type the annotation type
	 * @param consumer the consumer to accept annotated elements
	 */
	public void getBundledAnnotations(String type, Consumer<AnnotatedElement> consumer) {consumeAnnotations(type, consumer, project.getBundledDependencies());}

	private void consumeAnnotations(String type, Consumer<AnnotatedElement> consumer, List<Dependency> bundledDependencies) {
		for (Dependency dependency : bundledDependencies) {
			Set<AnnotatedElement> annotatedElements;
			try {
				annotatedElements = dependency.getAnnotations(this).annotatedBy(type);
			} catch (IOException e) {
				MCMake.LOGGER.warn("Could not get annotations for " + dependency, e);
				continue;
			}
			annotatedElements.forEach(consumer);
		}

		project.annotationRepo.annotatedBy(type).forEach(consumer);
	}

	/**
	 * Loads a mixin class node into the weaver for application during transformations.
	 *
	 * @param mixin the mixin class node
	 * @throws WeaveException if the mixin fails to load
	 */
	public void mixin(ClassNode mixin) throws WeaveException {project.persistentState.weaver.load(mixin);}
	/**
	 * Enables constant pool hooks and returns the hooks instance for transformation.
	 *
	 * @return the constant pool hooks
	 */
	public ConstantPoolHooks getCPHooks() {project.persistentState.needTransform = true;return project.persistentState.hooks;}
	/**
	 * Returns the mapper for class name and debug info remapping.
	 *
	 * @return the mapper instance
	 */
	public Mapper getMapper() {return project.persistentState.mapper;}

	/**
	 * Removes a class context from the classes list and adjusts indices and partitions.
	 *
	 * @param context the class context to remove
	 */
	public void removeClasses(Context context) {
		int i = classes.indexOf(context);
		classes.remove(i);

		int j = owners.indexAt(i);
		if (j >= 0) {
			IntervalPartition.Wrap<?> interval = owners.getSegments()[j].anchor().interval();
			interval.endPos--;

			for (j++; j < owners.getSegmentCount(); j++) {
				interval = owners.getSegments()[j].anchor().interval();
				interval.startPos--;
				interval.endPos--;
			}
		}

		if (i < compiledClassIndex) compiledClassIndex--;
	}

	@SuppressWarnings("unchecked")
	public <T extends Processor> @Nullable T getProcessor(Class<T> type) {
		return (T) Flow.of(processors).filter(p -> p.getClass() == type).findFirst().orElse(null);}
}