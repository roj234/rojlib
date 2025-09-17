package roj.event;

import roj.asm.Member;
import roj.ci.annotation.IndirectReference;

/**
 * @author Roj234
 * @since 2024/3/21 15:08
 */
final class ListenerInfo {
	// dot name
	final String event;
	final Member method;
	final byte flags;
	volatile EventListener impl;

	ListenerInfo(String event, Member method, byte flags) {
		this.event = event;
		this.method = method;
		this.flags = flags;
	}

	static final class MapEntry {
		// dot name
		final String owner;
		final ListenerInfo[] objectList, staticList;

		@IndirectReference
		MapEntry _next;

		MapEntry(String owner, ListenerInfo[] objectList, ListenerInfo[] staticList) {
			this.owner = owner;
			this.objectList = objectList;
			this.staticList = staticList;
		}
	}
}