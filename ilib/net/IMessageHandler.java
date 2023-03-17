package ilib.net;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface IMessageHandler<E extends IMessage> {
	void onMessage(E message, MessageContext ctx);
}