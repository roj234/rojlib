package ilib.api.chat;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/9/19 20:49
 */
public class LambdaChatProcessor implements ChatProcessor {
	Node head, tail;

	public LambdaChatProcessor then(LinkedProcessor processor) {
		if (head == null) {
			head = tail = new Node(processor);
		} else {
			tail.next = new Node(processor);
			tail = tail.next;
		}
		return this;
	}

	@Override
	public ChatProcessor processChat(String chat, ChatContext context) {
		Node node = (Node) context.customData.get("currNode");
		if (node == null) {
			context.customData.put("currNode", head);
			node = head;
		}
		if (node.processor.onChat(chat, context)) {
			if (node.next == null) return END;
			context.customData.put("currNode", node.next);
		}
		return this;
	}

	private static class Node {
		Node next;
		final LinkedProcessor processor;

		private Node(LinkedProcessor processor) {
			this.processor = processor;
		}
	}

	public interface LinkedProcessor {
		boolean onChat(String text, ChatContext context);
	}
}
