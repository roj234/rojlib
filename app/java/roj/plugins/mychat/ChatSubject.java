package roj.plugins.mychat;

import roj.util.ByteList;

/**
 * @author Roj233
 * @since 2022/3/9 13:31
 */
abstract class ChatSubject {
	public int id;
	public String name, face, desc;
	public byte flag;

	public abstract void serialize(ByteList b);
	public abstract void sendMessage(ChatManager c, Message m, boolean sys);
}