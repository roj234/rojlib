package roj.mapper;

import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.text.CharList;

public class LeveledBooleanRule {
	private final ToIntMap<CharSequence> map = new ToIntMap<>();

	public void serialize(CharList cl) {
		MyHashSet<String> inherit = new MyHashSet<>();
		SimpleList<ToIntMap.Entry<CharSequence>> sorted = new SimpleList<>(map.selfEntrySet());
		for (int i = sorted.size() - 1; i >= 0; i--) {
			String k = sorted.get(i).k.toString();
			if (k.endsWith("|")) {
				inherit.add(k.substring(0, k.length() - 1));
				sorted.remove(i);
			}
		}

		sorted.sort((o1, o2) -> {
			int v = Integer.compare(o2.v >>> 1, o1.v >>> 1);
			if (v != 0) return v;
			v = Integer.compare(getLevel(o2), getLevel(o1));
			if (v != 0) return v;
			return o1.toString().compareTo(o2.toString());
		});

		for (int i = 0; i < sorted.size(); i++) {
			ToIntMap.Entry<CharSequence> ent = sorted.get(i);
			switch (getLevel(ent)) {
				case 1:
					cl.append(inherit.remove(ent.k) ? "PI " : "P ");
					break;
				case 2:
					cl.append("C ");
					break;
				case 3:
					cl.append("M ");
					break;
			}
			cl.append(ent.k).append(' ').append(ent.v >>> 2).append(' ').append((ent.v & 1) != 0).append('\n');
		}
	}

	private static int getLevel(ToIntMap.Entry<CharSequence> ent) {
		String a = ent.k.toString();
		if (a.endsWith("|")) {
			// package-inherit
			return 0;
		} else if (a.contains("|")) {
			// method
			return 3;
		} else if ((ent.v & 2) != 0) {
			// package
			return 1;
		} else {
			// class
			return 2;
		}
	}

	public void setPackage(String pkg, boolean value, int priority, boolean inherit) {
		assert priority >= 0;
		if (inherit) {
			map.putInt(pkg, (priority << 2) | 2 | (value ? 1 : 0));
		}
		map.putInt(pkg + "|", (priority << 2) | (value ? 1 : 0));
	}

	public void setClass(String clz, boolean value, int priority) {
		assert priority >= 0;
		map.putInt(clz, (priority << 2) | (value ? 1 : 0));
	}

	public void setMethod(String clz, String method, boolean value, int priority) {
		assert priority >= 0;
		map.putInt(clz + "|" + method, (priority << 2) | (value ? 1 : 0));
	}

	public boolean get(String clz, String method, boolean def) {
		int priority = -1;
		int value = def ? 1 : 0;

		CharList cs = IOUtil.getSharedCharBuf().append(clz).append("|").append(method);
		ToIntMap.Entry<CharSequence> ent = map.getEntry(cs);
		if (ent != null) {
			priority = ent.v >>> 2;
			value = ent.v & 1;
		}

		cs.setLength(clz.length());
		ent = map.getEntry(cs);
		if (ent != null && (ent.v >>> 2) > priority) {
			priority = ent.v >>> 2;
			value = ent.v & 1;
		}

		cs.setLength(Math.max(0, cs.lastIndexOf("/")));
		cs.append('|');

		do {
			ent = map.getEntry(cs);
			if (ent != null && (ent.v >>> 2) > priority) {
				priority = ent.v >>> 2;
				value = ent.v & 1;
			}
		} while (trimPackage(cs));

		return value != 0;
	}

	private static boolean trimPackage(CharList cs) {
		int i = cs.lastIndexOf("/");
		if (i < 0) return false;
		cs.setLength(i);
		return true;
	}
}
