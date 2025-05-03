package roj.sql;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.util.Helpers;

import java.sql.Connection;

/**
 * 泛型数据库访问对象（DAO）接口，用于动态生成基于接口和SQL注解的数据库操作代理实现。
 * <p>
 * 通过 {@link #forType(Class)} 方法获取指定接口类型的代理实例，该实例会根据接口方法上的 {@link Query}
 * 注解自动生成SQL并执行。支持线程安全的缓存机制，避免重复生成代理类。
 *
 * <p>Name注解在Pojo类中
 * <p>
 * 标记类属性与数据库列的映射关系，用于解决属性名与列名不一致的场景。
 * 此注解可应用于类的字段上，支持以下功能：
 * <ul>
 *   <li><b>基础映射</b>：当数据库列名与类属性名不同时，显式指定列名。</li>
 *   <li><b>嵌套属性</b>：通过点号（.）语法映射关联对象的嵌套属性（如 {@code "address.city"}）。</li>
 *   <li><b>别名</b>：在查询结果集中为列定义别名，便于复杂查询（如联表查询）的结果映射。</li>
 * </ul>
 *
 * <p><b>示例：</b></p>
 * <pre>{@code
 * public class User {
 *     @Name("user_id")          // 映射到数据库列 `user_id`
 *     private int id;
 *
 *     @Name("full_name")        // 映射到数据库列 `full_name`
 *     private String name;
 *
 *     @Name("addr.city")        // 映射到嵌套对象 `addr` 的 `city` 列
 *     private String city;
 * }
 * }</pre>
 *
 * <p><b>注意事项：</b></p>
 * <ul>
 *   <li>若未添加此注解，默认使用字段名（不做任何处理）作为列名。</li>
 *   <li>嵌套属性映射依然只是Literally的列名。</li>
 * </ul>
 *
 * @param <T> DAO接口的具体类型（需包含 {@link Query} 注解的方法）
 * @see Query
 * @see roj.config.auto.Name 列名映射
 * @author Roj234-N
 * @since 2024/9/2 3:03
 */
public interface DAO<T> {
	/**
	 * 获取或创建指定DAO接口类型的代理实现实例。
	 * <p>
	 * 相同的{@link Class 类}会返回相同且缓存的实例。
	 * 生成的代理实例会解析接口方法上的 {@link Query} 注解，动态执行SQL。
	 *
	 * @param dao 目标DAO接口的 {@link Class} 对象，必须是非空接口类型
	 * @param <T> DAO接口的具体类型
	 * @return 已缓存的或新生成的DAO代理实例
	 * @throws IllegalArgumentException 如果参数 {@code dao} 不是接口
	 * @throws ReflectiveOperationException 如果代理类生成失败（隐式抛出无需显式catch，因为这是编程错误而不是运行时错误）
	 */
	@SuppressWarnings("unchecked")
	@NotNull
	static <T> DAO<T> forType(Class<T> dao) {
		var map = DAOMaker.IMPLEMENTATION_CACHE.computeIfAbsent(dao.getClassLoader(), Helpers.fnMyHashMap());

		var impl = map.get(dao);
		if (impl == null) {
			synchronized (map) {
				if ((impl = map.get(dao)) == null) {
					try {
						impl = DAOMaker.make(dao);
					} catch (ReflectiveOperationException e) {
						Helpers.athrow(e);
					}
					map.put(dao, impl);
				}
			}
		}

		return (DAO<T>) impl;
	}

	/**
	 * 创建一个与指定数据库连接关联的新DAO实例。
	 * @param connection 数据库连接对象，必须为开启状态
	 * @return 绑定到该连接的新DAO实例，永不为 {@code null}
	 * @throws NullPointerException 如果 {@code connection} 为 {@code null}
	 */
	@NotNull
	@Contract("_ -> new")
	T newInstance(Connection connection);
}
