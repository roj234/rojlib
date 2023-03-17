package ilib.api.chat;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/9/19 20:40
 */
public interface ChatProcessor {
	ChatProcessor END = (chat, context) -> null;

	ChatProcessor processChat(String chat, ChatContext context);
}
