package roj.sql;

import org.jetbrains.annotations.ApiStatus;
import roj.reflect.Bypass;
import roj.reflect.Reflection;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * @author Roj233
 * @since 2021/8/8 15:33
 */
public interface DataSource {
	Connection connect() throws SQLException;
	default ConnectionPool pooled(int connections) {return new ConnectionPool(this, connections);}

	static DataSource _default() {return jdbc("java.lang.Object", "jdbc:default:connection", null, null);}

	@ApiStatus.Experimental
	static DataSource h2(File db) {return jdbc("org.h2.Driver", "jdbc:h2:"+db.getAbsolutePath(), null, null);}

	static DataSource sqlite(File db) {return sqlite(db, null);}
	static DataSource sqlite(File db, String pass) {return jdbc("org.sqlite.JDBC", "jdbc:sqlite:"+db.getAbsolutePath(), null, pass);}

	static DataSource mariadb(String url, String database, String user, String pass) {return mariadb(url+"/"+database, user, pass);}
	static DataSource mariadb(String url, String user, String pass) {return jdbc("org.mariadb.jdbc.Driver", "jdbc:mariadb://"+url+"?characterEncoding="+DEFAULT_CHARSET, user, pass);}

	static DataSource mysql(String url, String database, String user, String pass) {return mysql(url+"/"+database, user, pass);}
	static DataSource mysql(String url, String user, String pass) {return jdbc("com.mysql.jdbc.Driver", "jdbc:mysql://"+url+"?characterEncoding="+DEFAULT_CHARSET, user, pass);}

	String DEFAULT_CHARSET = "UTF-16LE";
	private static DataSource jdbc(String driverClass, String url, String user, String pass) {
		ClassLoader classLoader = Reflection.getCallerClass(3, DataSource.class).getClassLoader();
		boolean bypassClassLoaderRestriction = classLoader != DataSource.class.getClassLoader();
		try {
			Class.forName(driverClass, true, classLoader);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("找不到数据库驱动程序:"+driverClass);
		}

		var info = new Properties();
		if (user != null) info.put("user", user);
		if (pass != null) info.put("password", pass);

		return bypassClassLoaderRestriction ? () -> {
			Thread.currentThread().setContextClassLoader(classLoader);
			return GetConnection.INSTANCE.getConnection(url, info, null);
		} : () -> (DriverManager.getConnection(url, info));
	}

	static interface GetConnection {
		GetConnection INSTANCE = Bypass.builder(GetConnection.class).delegate(DriverManager.class, "getConnection").build();

		Connection getConnection(String url, Properties info, Class<?> caller);
	}
}
