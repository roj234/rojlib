package roj.net.mychat;

import roj.config.serial.StreamSerializable;
import roj.config.serial.StreamSerializer;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
public class Message implements StreamSerializable {
    public int uid;
    public String text;
    public long time;

    public Message() {}

    public Message(int uid, String text) {
        this.uid = uid;
        this.text = text;
        this.time = System.currentTimeMillis();
    }

    @Override
    public void serialize(StreamSerializer ser) {
        ser.valueMap();

        ser.key("uid");
        ser.value(uid);

        ser.key("text");
        ser.value(text);

        ser.key("time");
        ser.value(time);

        ser.pop();
    }
}
