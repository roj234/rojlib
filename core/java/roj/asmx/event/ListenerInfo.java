package roj.asmx.event;

import roj.asm.Member;

/**
 * @author Roj234
 * @since 2024/3/21 15:08
 */
final class ListenerInfo {
	final String event;
	final Member mn;
	final byte flags;
	volatile EventListener impl;

	ListenerInfo(String event, Member mn, byte flags) {
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