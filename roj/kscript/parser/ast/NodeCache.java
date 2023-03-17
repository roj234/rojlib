package roj.kscript.parser.ast;

import roj.asm.Opcodes;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.collect.CharMap;

/**
 * @author solo6975
 * @since 2021/6/27 13:31
 */
public class NodeCache {
	static final ThreadLocal<CharMap<InsnNode>> CACHE = ThreadLocal.withInitial(CharMap::new);

	public static InsnNode a_asBool_0() {
		InsnNode node = CACHE.get().get('\0');
		if (node == null) CACHE.get().put('\0', node = new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/KType", "asBool", "()Z"));
		return node;
	}

	public static InsnNode a_asBool_1() {
		InsnNode node = CACHE.get().get('\1');
		if (node == null) CACHE.get().put('\1', node = new InvokeInsnNode(Opcodes.INVOKESTATIC, "roj/kscript/type/KBool", "valueOf", "(Z)Lroj/kscript/type/KBool;"));
		return node;
	}

	public static InsnNode a_asInt_0() {
		InsnNode node = CACHE.get().get('\2');
		if (node == null) CACHE.get().put('\2', node = new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/KType", "asBool", "()Z"));
		return node;
	}

	public static InsnNode a_field_0() {
		InsnNode node = CACHE.get().get('\3');
		if (node == null) CACHE.get().put('\3', node = new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/IObject", "delete", "(Ljava/lang/String;)Z"));
		return node;
	}

	public static InsnNode a_field_1() {
		InsnNode node = CACHE.get().get('\4');
		if (node == null) CACHE.get().put('\4', node = new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/IObject", "get", "(Ljava/lang/String;)Lroj/kscript/type/KType;"));
		return node;
	}
}
