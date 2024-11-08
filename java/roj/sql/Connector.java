package roj.sql;

import roj.Beta;
import roj.reflect.ReflectionUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author Roj233
 * @since 2021/8/8 15:33
 */
public interface Connector {
	Connection connect() throws SQLException;

	static Connector _default() {return jdbc("java.lang.Object", "jdbc:default:connection", null, null);}

	@Beta
	static Connector h2(File db) {return jdbc("org.h2.Driver", "jdbc:h2:"+db.getAbsolutePath(), null, null);}

	static Connector sqlite(File db) {return sqlite(db, null);}
	static Connector sqlite(File db, String pass) {return jdbc("org.sqlite.JDBC", "jdbc:sqlite:"+db.getAbsolutePath(), null, pass);}

	static Connector mariadb(String url, String database, String user, String pass) {return mariadb(url+"/"+database, user, pass);}
	static Connector mariadb(String url, String user, String pass) {return jdbc("org.mariadb.jdbc.Driver", "jdbc:mariadb://"+url+"?characterEncoding="+MY_DB_CHARSET, user, pass);}

	static Connector mysql(String url, String database, String user, String pass) {return mysql(url+"/"+database, user, pass);}
	static Connector mysql(String url, String user, String pass) {return jdbc("com.mysql.jdbc.Driver", "jdbc:mysql://"+url+"?characterEncoding="+MY_DB_CHARSET, user, pass);}

	String MY_DB_CHARSET = "UTF-16LE";
	private static Connector jdbc(String driverClass, String url, String user, String pass) {
		try {
			Class.forName(driverClass, true, ReflectionUtils.getCallerClass(4).getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("找不到数据库驱动程序:"+driverClass);
		}
		return () -> DriverManager.getConnection(url, user, pass);
	}
}
