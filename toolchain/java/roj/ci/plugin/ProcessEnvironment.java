package roj.ci.plugin;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import roj.archive.zip.ZipOutput;
import roj.asm.ClassNode;
import roj.asm.attr.Annotations;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.Context;
import roj.asmx.TransformException;
import roj.asmx.injector.CodeWeaver;
import roj.asmx.injector.WeaveException;
import roj.asmx.mapper.Mapper;
import roj.collect.*;
import roj.util.function.ExceptionalSupplier;
import roj.gui.Profiler;
import roj.ci.Project;
import roj.ui.Tty;
import roj.util.ByteList;
import roj.util.function.Flow;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Roj234
 * @since 2023/1/19 8:29
 */
public class ProcessEnvironment {
	public final Project project;
	public final List<Processor> processors;

	public static final int INC_FULL = 0, INC_REBUILD = 1, INC_LOAD = 2, INC_UPDATE = 3;
	@MagicConstant(intValues = {INC_FULL, INC_REBUILD, INC_LOAD, INC_UPDATE})
	public int increment;

	public int compiledClassIndex, updateCount;

	private final Map<String, Object> generatedFiles = new HashMap<>();

	private Map<String, List<Context>> byAnnotation;

	public ProcessEnvironment(Project p) {
		this.project = p;
		this.processors = p.workspace.getProcessors();
	}

	public static class CacheState {
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

	public void initCache() {
		if (increment == INC_FULL) {
			project.cacheState = new CacheState();
		} else if (project.cacheState == null) {
			increment = Math.min(increment, INC_LOAD);
			project.cacheState = new CacheState();
		}
	}

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
	public void setClasses(List<Context> classes) {this.classes = classes;this.compiledClassIndex = classes.size();}
	public void addClass(Context context, Project project, boolean writable) {
		myClasses.computeIfAbsent(project, Helpers.fnArrayList()).add(context);
		if (writable && increment == INC_LOAD) changed.add(context);
	}

	public void addFile(String name, ByteList data) {generatedFiles.put(name, data);}

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

		Profiler.endStartSection("processor");
		for (int i = 0; i < processors.size(); i++) {
			processors.get(i).afterCompile(this);
		}
		this.classes = Collections.emptyList();

		Profiler.endStartSection("transformer");
		try {
			project.cacheState.runTransformers(classes);
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

	@SuppressWarnings("unchecked")
	public int writeExtra(ZipOutput mappedWriter) throws IOException {
		for (var entry : generatedFiles.entrySet()) {
			if (entry.getValue() instanceof ByteList b) {
				mappedWriter.set(entry.getKey(), b);
			} else {
				mappedWriter.set(entry.getKey(), (ExceptionalSupplier<ByteList, IOException>) entry.getValue());
			}
		}
		return generatedFiles.size();
	}

	@UnmodifiableView
	public List<Context> getClasses() {return classes;}

	public Map<String, ?> getVariables(Context context) {
		IntervalPartition.Segment segment = owners.segmentAt(classes.indexOf(context));
		if (segment == null) return project.variables;

		List<IntervalPartition.Wrap<Project>> coverage = segment.coverage();
		if (coverage.isEmpty()) return project.variables;

		return project.new TwoMap(coverage.get(0).obj.variables);
	}

	public List<Context> getAnnotatedClass(String annotation) {
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
		return byAnnotation.getOrDefault(annotation, Collections.emptyList());
	}

	public void mixin(ClassNode mixin) throws WeaveException {project.cacheState.weaver.load(mixin);}
	public ConstantPoolHooks getCPHooks() {project.cacheState.needTransform = true;return project.cacheState.hooks;}
	public Mapper getDynamicMapper() {return project.cacheState.mapper;}

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