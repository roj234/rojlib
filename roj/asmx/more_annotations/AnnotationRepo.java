package roj.asmx.more_annotations;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.asm.Parser;
import roj.asm.cp.ConstantPool;
import roj.asm.tree.Attributed;
import roj.asm.tree.CNode;
import roj.asm.tree.ConstantData;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.asmx.more_annotations.AnnotationInfo.ClassInfo;
import roj.asmx.more_annotations.AnnotationInfo.NodeInfo;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Roj234
 * @since 2023/12/26 0026 12:47
 */
public class AnnotationRepo {
	private final MyHashMap<String, Set<AnnotationInfo>> annotations = new MyHashMap<>();

	public Set<AnnotationInfo> annotatedBy(String type) { return annotations.getOrDefault(type, Collections.emptySet()); }

	public void add(File file) {
		try (ZipArchive za = new ZipArchive(file)) {
			for (ZEntry ze : za.getEntries().values()) {
				if (IOUtil.extensionName(ze.getName()).equalsIgnoreCase("class")) {
					ConstantData data = Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(za.getInput(ze)));
					if (data.name.concat(".class").equalsIgnoreCase(ze.getName())) {
						add(data);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void add(ConstantData data) {
		ClassInfo klass = new ClassInfo(data.name);
		addAnnotations(data.cp, data, klass);
		addChildren(data, data.methods, klass);
		addChildren(data, data.fields, klass);
	}
	private void addChildren(ConstantData data, List<? extends CNode> nodes, ClassInfo klass) {
		for (int i = 0; i < nodes.size(); i++) {
			CNode node = nodes.get(i);
			NodeInfo subNode = new NodeInfo(data.name, node.name(), node.rawDesc());
			addAnnotations(data.cp, node, subNode);
			klass.children.add(subNode);
		}
	}
	private void addAnnotations(ConstantPool cp, Attributed node, AnnotationInfo info) {
		Annotations attr = node.parsedAttr(cp, Attribute.RtAnnotations);
		if (attr != null) addAnnotations(attr.annotations, info);
		attr = node.parsedAttr(cp, Attribute.ClAnnotations);
		if (attr != null) addAnnotations(attr.annotations, info);
	}
	private void addAnnotations(List<Annotation> list, AnnotationInfo info) {
		for (int i = 0; i < list.size(); i++) {
			Annotation anno = list.get(i);

			info.annotations.put(anno.type, anno);
			annotations.computeIfAbsent(anno.type, Helpers.cast(Helpers.myhashsetfn)).add(info);
		}
	}
}