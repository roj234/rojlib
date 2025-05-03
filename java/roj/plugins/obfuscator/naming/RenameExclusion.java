package roj.plugins.obfuscator.naming;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.cp.Constant;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstRef;
import roj.asm.cp.CstString;
import roj.asm.insn.CodeVisitor;
import roj.collect.SimpleList;
import roj.config.auto.Optional;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.PermissionSet;

import java.util.List;

/**
 * 排除常见的不能混淆的情况
 * TODO: StackAnalyzer
 *
 * @author Roj233
 * @since 2022/2/20 22:18
 */
public class RenameExclusion extends CodeVisitor {
	public List<ExclusionEntry> exclusionEntries = new SimpleList<>();

	private final List<String> ldc = new SimpleList<>();
	private int ldcPos;

	public static final class ExclusionEntry {
		public String name;
		public int flag;
		@Optional
		public boolean inherit, priority;
		public transient boolean generated;

		@Override
		public String toString() {
			CharList sb = new CharList();
			sb.append(name).append(" (").append(flag);
			if (generated) sb.append(", 自动");
			if (inherit) sb.append(", 继承");
			if (priority) sb.append(", 优先");
			return sb.append(")").toStringAndFree();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			return name.equals(((ExclusionEntry) o).name);
		}

		@Override
		public int hashCode() { return name.hashCode(); }
	}

	public PermissionSet toRuleset() {
		PermissionSet ruleset = new PermissionSet("/");
		for (int i = 0; i < exclusionEntries.size(); i++) {
			ExclusionEntry entry = exclusionEntries.get(i);
			ruleset.add(entry.name, entry.flag, entry.priority, entry.inherit);
		}
		return ruleset;
	}
	public void checkExclusion(ClassNode data, int flag) {
		if (0 != (data.modifier() & Opcodes.ACC_ENUM) && (flag & 1) != 0) {
			addExclusion(data.name()+"//values", 4);
			addExclusion(data.name()+"//valueOf", 4);
			addExclusion(data.name(), 2);
		}
		if (0 != (data.modifier() & Opcodes.ACC_ANNOTATION) && (flag & 2) != 0) {
			addExclusion(data.name(), NameObfuscator.EX_METHOD);
		}

		ldcPos = 0;
		ByteList tmp = new ByteList();
		List<? extends MethodNode> methods = data.methods;
		for (int j = 0; j < methods.size(); j++) {
			MethodNode mn = methods.get(j);
			if ((mn.modifier()&Opcodes.ACC_NATIVE) != 0) {
				addExclusion(data.name(), 1);
				addExclusion(data.name() +"//"+mn.name(), 4);
			}

			mn.visitCode(this, data.cp, tmp);
		}
		tmp._free();
	}
	private void addExclusion(String name, int flag) {
		ExclusionEntry entry = new ExclusionEntry();
		entry.name = name;
		entry.flag = flag;
		entry.generated = true;

		int i = exclusionEntries.indexOf(entry);
		if (i < 0) exclusionEntries.add(entry);
		else exclusionEntries.get(i).flag |= flag;
	}

	@Override
	public void ldc(byte code, Constant c) {
		switch (c.type()) {
			case Constant.STRING:
				ldc.add(((CstString) c).name().str());
				ldcPos = bci;
			break;
			case Constant.CLASS:
				ldc.add(((CstClass) c).name().str());
				ldcPos = bci;
			break;
		}
	}

	@Override
	public void invoke(byte code, CstRef method) {
		String owner = method.owner();
		String name = method.name();
		String param = method.rawDesc();

		if (bci - ldcPos <= 3 && ldc.size() > 0) {
			if (code == Opcodes.INVOKESTATIC) {
				if (owner.startsWith("java/util/concurrent/atomic/Atomic") && owner.endsWith("FieldUpdater") && ldc.size() >= 2) {
					String exOwner = ldc.get(ldc.size() - (owner.endsWith("ReferenceFieldUpdater") ? 3 : 2));
					String exName = ldc.get(ldc.size() - 1);
					addExclusion(exOwner+"//"+exName, NameObfuscator.EX_FIELD);
				} else if (owner.equals("java/lang/Class") && name.equals("forName")) {
					addExclusion(ldc.get(ldc.size()-1).replace('.', '/'), NameObfuscator.EX_CLASS);
				} else if (owner.equals("roj/reflect/Bypass") && (name.equals("builder") || name.equals("custom"))) {
					addExclusion(ldc.get(ldc.size()-1), NameObfuscator.EX_METHOD);
				} else if (owner.equals("roj/reflect/ReflectionUtils")) {
					if ((name.equals("fieldOffset") || name.equals("getField")) && ldc.size() >= 2) {
						addExclusion(ldc.get(0)+"//"+ldc.get(1), NameObfuscator.EX_FIELD);
					}
				}
				block:
				if (owner.equals("roj/collect/XHashSet")) {
					int i = 0;
					if (name.equals("shape")) {
						i = 1;
					} else if (name.equals("noCreation")) {
						if (ldc.size() < 3) ldc.add("_next");
					} else {
						break block;
					}

					if (ldc.size() - i < 3) break block;
					var owner1 = ldc.get(i++);
					var field1 = ldc.get(i++);
					var field2 = ldc.get(i);
					addExclusion(owner1+"//"+field1, NameObfuscator.EX_FIELD);
					addExclusion(owner1+"//"+field2, NameObfuscator.EX_FIELD);
				}
			} else if (owner.equals("java/lang/Class") && name.startsWith("get") && (name.endsWith("Field") || name.endsWith("Method")) && ldc.size() >= 2) {
				addExclusion(ldc.get(0)+"//"+ldc.get(1), NameObfuscator.EX_METHOD|NameObfuscator.EX_FIELD);
			} else if (name.equals("getResourceAsStream") && owner.equals("java/lang/ClassLoader")) {
				String s = ldc.get(ldc.size() - 1);
				if (s.endsWith(".class")) {
					addExclusion(s.substring(0, s.length()-6), NameObfuscator.EX_CLASS|NameObfuscator.EX_METHOD|NameObfuscator.EX_FIELD);
				}
			}
		}

		ldc.clear();

		if (name.equals("main") && param.equals("([Ljava/lang/String;)V")) {
			addExclusion(owner+"//"+name, NameObfuscator.EX_METHOD);
		}
	}
}