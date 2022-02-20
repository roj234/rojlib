package roj.net.mychat;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
public final class Message {
    public int uid;
    public String text;
    public long time;

    public Message() {}

    public Message(int uid, String text) {
        this.uid = uid;
        this.text = text;
        this.time = System.currentTimeMillis();
    }
}
