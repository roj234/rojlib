package roj.asmx;

/**
 * @author Roj233
 * @since 2020/11/9 22:39
 */
public interface Transformer {
	boolean transform(String name, Context ctx) throws TransformException;
}