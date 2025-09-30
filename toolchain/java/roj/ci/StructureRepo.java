package roj.ci;

import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.Member;
import roj.asm.MemberDescriptor;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.type.Signature;
import roj.asmx.AnnotationRepo;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.util.Helpers;

import java.util.*;

/**
 * @author Roj234
 * @since 2025/10/1 0:01
 */
final class StructureRepo {
	private final Map<String, Object> structures = new HashMap<>();

	private static Object createStem(ClassNode node) {
		// non-public class永不传递
		if ((node.modifier&Opcodes.ACC_PUBLIC) == 0) return Collections.emptyList();

		List<Object> members = new ArrayList<>();
		fill(node, members, node.methods);
		fill(node, members, node.fields);

		Comparator<MemberDescriptor> comparator = (o1, o2) -> {
			int i = o1.name.compareTo(o2.name);
			if (i != 0) return i;
			return o1.rawDesc.compareTo(o2.rawDesc);
		};
		members.sort(Helpers.cast(comparator));

		members.add(node.parent());
		members.addAll(node.interfaces());

		return members;
	}
	private static void fill(ClassNode node, List<Object> members, List<? extends Member> methods) {
		for (Member method : methods) {
			if ((method.modifier()&(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED)) == 0) continue;

			Signature signature = method.getAttribute(node.cp, Attribute.SIGNATURE);
			members.add(new MemberDescriptor(node.name(), method.name(), signature != null ? signature.toDesc() : method.rawDesc()));
		}
	}

	public boolean isEmpty() {return structures.isEmpty();}
	public void add(ClassNode node) {structures.put(node.name(), createStem(node));}
	public void remove(String name) {structures.remove(name);}

	public boolean update(@Nullable Object originalStem, ClassNode data) {
		Object stem = createStem(data);
		if (stem.equals(originalStem)) return false;
		structures.put(data.name(), stem);
		return true;
	}

	private Set<String> removed = Collections.emptySet();
	public void fileRemoved(String className) {
		if (removed.isEmpty()) removed = new HashSet<>();
		removed.add(className);
	}

	public Map<String, Object> applyDiff(Set<String> changed) {
		var handles = new HashMap<String, Object>();
		for (var itr = structures.entrySet().iterator(); itr.hasNext(); ) {
			var entry = itr.next();
			String normalizeName = AnnotationRepo.normalizeName(entry.getKey());
			if (removed.contains(normalizeName)) itr.remove();
			else if (changed.contains(normalizeName)) {
				handles.put(entry.getKey(), entry.getValue());
			}
		}
		removed = Collections.emptySet();
		return handles;
	}

	public void clear() {structures.clear();}
}
