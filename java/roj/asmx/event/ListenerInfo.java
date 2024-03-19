package roj.asmx.event;

import roj.asm.tree.MethodNode;

/**
 * @author Roj234
 * @since 2024/3/21 0021 15:08
 */
final class ListenerInfo {
	final String event;
	final MethodNode mn;
	final byte flags;
	volatile EventListener impl;

	ListenerInfo(String event, MethodNode mn, byte flags) {
		this.event = event;
		this.mn = mn;
		this.flags = flags;
	}

	static final class MapEntry {
		final String owner;
		final ListenerInfo[] objectList, staticList;

		MapEntry next;

		MapEntry(String owner, ListenerInfo[] objectList, ListenerInfo[] staticList) {
			this.owner = owner;
			this.objectList = objectList;
			this.staticList = staticList;
		}
	}
}