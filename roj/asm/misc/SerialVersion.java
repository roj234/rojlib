package roj.asm.misc;

import roj.asm.cst.CstLong;
import roj.asm.tree.Field;
import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.asm.tree.attr.ConstantValue;
import roj.asm.type.Type;
import roj.collect.BSLowHeap;
import roj.util.ByteList;
import roj.util.Helpers;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;

import static roj.asm.util.AccessFlag.*;

/**
 * 计算SerialVersionUID
 *
 * @author Roj233
 * @since 2021/8/29 1:58
 */
public class SerialVersion {
	static final Comparator<Item> ITEM_SORTER = (o1, o2) -> {
		int v = o1.name.compareTo(o2.name);
		if (v != 0) return v;
		return o1.desc.compareTo(o2.desc);
	};
	static final AtomicReference<MessageDigest> LOCAL_SHA = new AtomicReference<>();

	public static boolean computeIfNotPresent(IClass cz) {
		int index = cz.getField("serialVersionUID");
		if (index != -1 && cz.fields().get(index).rawDesc().equals("J")) {
			return false;
		}

		BSLowHeap<Item> constructors = new BSLowHeap<>(ITEM_SORTER);
		BSLowHeap<Item> methods = new BSLowHeap<>(ITEM_SORTER);

		boolean clInit = false;
		for (MoFNode node : cz.methods()) {
			String name = node.name();
			if ("<clinit>".equals(name)) {
				clInit = true;
				continue;
			}

			int access = node.modifier() & (PUBLIC | PRIVATE | PROTECTED | STATIC | FINAL | SYNCHRONIZED | NATIVE | ABSTRACT | STRICTFP);
			if ((access & PRIVATE) == 0) {
				("<init>".equals(name) ? constructors : methods).add(new Item(name, node.rawDesc(), access));
			}
		}

		ByteList w = new ByteList(128);
		w.putUTF(cz.name().replace('/', '.'));
		int access = cz.modifier();
		if ((access & INTERFACE) != 0) {
			access = methods.size() > 0 ? access | ABSTRACT : access & -1025;
		}

		w.putInt(access & (PUBLIC | FINAL | INTERFACE | ABSTRACT));
		String[] itf = cz.interfaces().toArray(new String[cz.interfaces().size()]);
		Arrays.sort(itf);
		for (String s : itf) {
			w.putUTF(s.replace('/', '.'));
		}

		BSLowHeap<Item> fields = new BSLowHeap<>(ITEM_SORTER);
		for (MoFNode node : cz.fields()) {
			access = node.modifier();
			if ((access & PRIVATE) == 0 || (access & (STATIC | TRANSIENT)) == 0) {
				access &= (PUBLIC | PROTECTED | STATIC | FINAL | VOLATILE);
				fields.add(new Item(node.name(), node.rawDesc(), access));
			}
		}
		for (int i = 0; i < fields.size(); ++i) {
			w.putUTF(fields.get(i).name);
			w.putInt(fields.get(i).access).putUTF(fields.get(i).desc);
		}
		if (clInit) {
			w.putUTF("<clinit>");
			w.putInt(STATIC).putUTF("()V");
		}

		for (int i = 0; i < constructors.size(); ++i) {
			w.putUTF(constructors.get(i).name);
			w.putInt(constructors.get(i).access).putUTF(constructors.get(i).desc.replace('/', '.'));
		}
		for (int i = 0; i < methods.size(); ++i) {
			w.putUTF(methods.get(i).name);
			w.putInt(methods.get(i).access).putUTF(methods.get(i).desc.replace('/', '.'));
		}

		MessageDigest localSha = LOCAL_SHA.getAndSet(null);
		if (localSha == null) {
			try {
				localSha = MessageDigest.getInstance("SHA");
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException("Unexpected: 不支持SHA1");
			}
			localSha.update(w.list, 0, w.wIndex());

			w.clear();
			w.ensureCapacity(20);
			w.wIndex(8);
			try {
				localSha.digest(w.list, 0, 20);
			} catch (DigestException e) {
				throw new RuntimeException("Unexpected: SHA1 Error", e);
			}
		}
		LOCAL_SHA.compareAndSet(null, localSha);

		long svuid = 0;
		for (int i = 7; i >= 0; --i) {
			svuid = svuid << 8 | (long) (w.list[i] & 255);
		}

		Field fl = new Field(STATIC | FINAL, "serialVersionUID", Type.std(Type.LONG));
		fl.attributes().putByName(new ConstantValue(new CstLong(svuid)));
		cz.fields().add(Helpers.cast(fl));
		return true;
	}

	static final class Item {
		String name, desc;
		char access;

		public Item(String name, String desc, int access) {
			this.name = name;
			this.desc = desc;
			this.access = (char) access;
		}
	}
}
