package roj.plugins.ci.plugin;

import roj.asm.tree.ConstantData;
import roj.asm.tree.anno.Annotation;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.plugins.ci.Project;
import roj.util.ByteList;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/1/19 0019 8:29
 */
public class ProcessEnvironment {
	public Project project;

	public int increment;
	public int changedClassIndex;

	public Map<String, ByteList> generatedFiles = new MyHashMap<>();

	private Map<String, List<Context>> byAnnotation;

	public List<Context> getAnnotatedClass(List<Context> list, String annotation) {
		if (byAnnotation == null) {
			byAnnotation = new MyHashMap<>();
			for (int j = 0; j < list.size(); j++) {
				Context ctx = list.get(j);
				ConstantData data = ctx.getData();

				var anns = Annotation.getAnnotations(data.cp, data, false);
				for (int i = 0; i < anns.size(); i++) {
					byAnnotation.computeIfAbsent(anns.get(i).type(), Helpers.fnArrayList()).add(ctx);
				}

				anns = Annotation.getAnnotations(data.cp, data, true);
				for (int i = 0; i < anns.size(); i++) {
					byAnnotation.computeIfAbsent(anns.get(i).type(), Helpers.fnArrayList()).add(ctx);
				}
			}
		}
		return byAnnotation.getOrDefault(annotation, Collections.emptyList());
	}
}