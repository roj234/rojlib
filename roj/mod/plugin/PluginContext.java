package roj.mod.plugin;

import roj.asm.tree.ConstantData;
import roj.asm.tree.anno.Annotation;
import roj.asm.util.Attributes;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/1/19 0019 8:29
 */
public class PluginContext {
	private Map<String, List<Context>> byAnnotation;

	public List<Context> getAnnotatedClass(List<Context> list, String annotation) {
		if (byAnnotation == null) {
			byAnnotation = new MyHashMap<>();
			for (int j = 0; j < list.size(); j++) {
				Context ctx = list.get(j);
				ConstantData data = ctx.getData();

				List<Annotation> anns = Attributes.getAnnotations(data.cp, data, false);
				if (anns != null) {
					for (int i = 0; i < anns.size(); i++) {
						byAnnotation.computeIfAbsent(anns.get(i).type, Helpers.fnArrayList()).add(ctx);
					}
				}

				anns = Attributes.getAnnotations(data.cp, data, true);
				if (anns != null) {
					for (int i = 0; i < anns.size(); i++) {
						byAnnotation.computeIfAbsent(anns.get(i).type, Helpers.fnArrayList()).add(ctx);
					}
				}
			}
		}
		return byAnnotation.getOrDefault(annotation, Collections.emptyList());
	}
}