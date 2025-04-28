package roj.compiler.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记该类可通过索引访问.
 * 编译器会将这个类的 foreach 循环优化为索引循环。
 * 需要实现以下两个方法：
 * <ul>
 *   <li>{@code T get(int index)} - 提供索引访问</li>
 *   <li>{@code int size()}       - 提供元素数量</li>
 * </ul>
 * 也(feature?)直接允许了foreach，如果这个类未实现Iterable的话
 * 为了和不支持该特性的编译器兼容，建议也实现 {@link Iterable} 接口。
 *
 * <p>
 * 示例：
 * <pre>{@code
 * @RandomAccessible
 * public class StringList implements Iterable<String> {
 *     private final String[] data = {"Hello", "World"};
 *
 *     public String get(int index) {return data[index];}
 *
 *     public int size() {return data.length;}
 *
 *     // 为了兼容性（非必须但建议），提供默认迭代器实现
 *     @Override
 *     public Iterator<String> iterator() {
 *         return new Iterator<>() {
 *             private int pos;
 *             @Override
 *             public boolean hasNext() { return pos < size(); }
 *             @Override
 *             public String next() { return get(pos++); }
 *         };
 *     }
 *
 *     public static void main(String[] args) {
 *         StringList list = new StringList();
 *         // 优化为：
 *         // for (int i=0; i<list.size(); i++) System.out.println(list.get(i));
 *         for (String s : list) {
 *             System.out.println(s);
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author Roj234
 * @since 2024/6/30 0030 18:11
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface RandomAccessible {}
