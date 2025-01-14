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
	 * 使用Java11新增的NestMember属性而不是生成access方法来允许内部类的访问
	 * Use Java11's new NestMember attribute instead of generating an access method to allow access to an inner class.
	 * <a href="https://www.baeldung.com/java-nest-based-access-control">Nest based access control</a>
	 */
	int NESTED_MEMBER = 11;
	/**
	 * 不编译assert语句
	 * Dont compile assert statements
	 * */
	int DISABLE_ASSERT = 12;
	/**
	 * 使用Java17新增的PermittedClass属性来更好的控制抽象枚举类的可继承性
	 * Use Java17's new PermittedClass attribute to better control the inheritability of abstract enumerated classes
	 * */
	int SEALED_ENUM = 13;
	/**
	 * 将‘抛出检查的异常’的诊断等级从错误下调为严重警告
	 * 同时将允许try捕获未抛出的检查异常
	 * Downgraded the diagnostic level of 'Throwing unchecked exception' from ERROR to WARNING.
	 */
	int NO_CHECKED_EXCEPTION = 14;


	// no stack frame
	int COMPATIBILITY_LEVEL_JAVA_6 = 6;
	// lambda
	int COMPATIBILITY_LEVEL_JAVA_8 = 8;
	// module
	int COMPATIBILITY_LEVEL_JAVA_9 = 9;
	// constant_dynamic
	int COMPATIBILITY_LEVEL_JAVA_11 = 11;
	// record
	int COMPATIBILITY_LEVEL_JAVA_17 = 17;
	// string template via lambda
	int COMPATIBILITY_LEVEL_JAVA_21 = 21;
}