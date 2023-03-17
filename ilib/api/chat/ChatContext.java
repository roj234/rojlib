package ilib.api.chat;

import roj.collect.MyHashMap;

import net.minecraft.entity.player.EntityPlayerMP;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2020/9/19 20:39
 */
public class ChatContext {
	public final EntityPlayerMP player;
	public final List<String> playerSaid = new LinkedList<>();

	public Map<String, Object> customData = new MyHashMap<>();

	protected ChatProcessor currentProcessor;

	protected ChatContext(EntityPlayerMP player, ChatProcessor processor) {
		this.player = player;
		this.currentProcessor = processor;
	}

	public ChatProcessor getCurrentProcessor() {
		return currentProcessor;
	}
}
