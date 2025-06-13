package roj.plugins.ci.plugin;

import roj.asm.ClassNode;
import roj.asm.attr.Annotations;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.Context;
import roj.asmx.TransformException;
import roj.asmx.injector.CodeWeaver;
import roj.asmx.injector.WeaveException;
import roj.collect.HashMap;
import roj.plugins.ci.Project;
import roj.util.ByteList;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/1/19 8:29
 */
public class ProcessEnvironment {
	public Project project;

	public int increment;
	public int changedClassIndex;

	public final Map<String, ByteList> generatedFiles = new HashMap<>();

	private Map<String, List<Context>> byAnnotation;
	private final CodeWeaver weaver = new CodeWeaver();
	private final ConstantPoolHooks hooks = new ConstantPoolHooks();
	private boolean needTransform;

	public List<Context> getAnnotatedClass(List<Context> compilerOutput, String annotation) {
		if (byAnnotation == null) {
			byAnnotation = new HashMap<>();
			for (int j = 0; j < compilerOutput.size(); j++) {
				Context ctx = compilerOutput.get(j);
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

	public void addMixin(ClassNode mixin) throws WeaveException {weaver.load(mixin);}
	public ConstantPoolHooks getCPHooks() {needTransform = true;return hooks;}

	public void runTransformers(List<Context> compilerOutput) throws TransformException {
		if (!weaver.registry().isEmpty()) {
			for (int i = 0; i < compilerOutput.size(); i++) {
				Context context = compilerOutput.get(i);
				weaver.transform(context.getClassName(), context);
			}
		}
		if (needTransform) {
			for (int i = 0; i < compilerOutput.size(); i++) {
				Context context = compilerOutput.get(i);
				hooks.transform(context.getClassName(), context);
			}
		}
	}
}