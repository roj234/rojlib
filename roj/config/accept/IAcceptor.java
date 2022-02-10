package roj.config.accept;

/**
 * @author Roj234
 * @since 2022/1/29 8:44
 */
public interface IAcceptor {
    void value(int l);
    void value(String l);
    void value(long l);
    void value(double l);
    void value(boolean l);
    void valueNull();
    void valueMap();
    void valueList();

    void key(String key);
    void pop();
}
