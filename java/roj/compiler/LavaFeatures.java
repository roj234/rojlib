package roj.compiler;

import roj.text.CharList;

public interface LavaFeatures {
	// not change bytecode
	/**
	 * 生成MethodParameters属性(方法参数名)
	 */
	int ATTR_METHOD_PARAMETERS = 0;
	/**
	 * 生成SourceFile属性(源文件)
	 */
	int ATTR_SOURCE_FILE = 1;
	/**
	 * 生成LineNumberTable属性(行号)
	 */
	int ATTR_LINE_NUMBERS = 2;
	/**
	 * 生成LocalVariable及其泛型变种(方法变量类型)
	 * Not implemented
	 */
	int ATTR_LOCAL_VARIABLES = 3;
	/**
	 * 生成InnerClasses属性(内部类更详细的modifier及引用)
	 * WIP
	 */
	int ATTR_INNER_CLASS = 4;
	/**
	 * 生成StackMapTable
	 * Not implemented
	 */
	int ATTR_STACK_FRAME = 5;

	/**
	 * 启用'可选的分号'
	 */
	int OPTIONAL_SEMICOLON = 6;
	/**
	 * 启用'文件名必须和公共类名相同'检查
	 */
	int VERIFY_FILENAME = 7;
	/**
	 * 创建对象时允许省略new关键字
	 * Allows the omission of the new keyword to create objects
	 */
	int OMISSION_NEW = 8;

	// change bytecode
	/**
	 * 使用带有数组缓存的CharList而不是StringBuilder进行字符串加法
	 * String addition using CharList with array caching instead of StringBuilder
	 * @see CharList#_free()
	 */
	int SHARED_STRING_CONCAT = 10;
	/**
	 * 不编译assert语句
	 * Dont compile assert statements
	 * */
	int DISABLE_ASSERT = 11;
	/**
	 * 将‘抛出检查的异常’的诊断等级从错误下调为严重警告
	 * 同时将允许try捕获未抛出的检查异常
	 * Downgraded the diagnostic level of 'Throwing unchecked exception' from ERROR to WARNING.
	 */
	int DISABLE_CHECKED_EXCEPTION = 12;
	/**
	 * 将record类型的字段从private final提升为public final
	 */
	int PUBLIC_RECORD_FIELD = 13;
	/**
	 * 当synchronized对Lock的子类使用时，会调用其lock和unlock方法，而不是monitorEnter/exit
	 */
	int SYNCHRONIZED_LOCK = 14;
	/**
	 * 常量传播，通过VisMap，int test = 5 这种语句也能被提取常量，建议在调试时关闭
	 */
	int CONSTANT_SPREAD = 15;

	// lambda
	int JAVA_8 = 8;
	// module
	int JAVA_9 = 9;
	/**
	 * constant_dynamic,
	 * 使用Java11新增的NestMember属性而不是生成access方法来允许内部类的访问
	 * Use Java11's new NestMember attribute instead of generating an access method to allow access to an inner class.
	 * <a href="https://www.baeldung.com/java-nest-based-access-control">Nest based access control</a>
	 */
	int JAVA_11 = 11;
	/**
	 * Record,
	 * 使用Java17新增的PermittedClass属性来更好的控制抽象枚举类的可继承性
	 * Use Java17's new PermittedClass attribute to better control the inheritability of abstract enumerated classes
	 * */
	int JAVA_17 = 17;
	// string template via lambda
	int JAVA_21 = 21;
}