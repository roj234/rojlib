package roj.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author Roj233
 * @since 2021/8/3 15:26
 */
public final class MySql extends DbConnector {
	private final String name, pass, url;

	static {
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException ignored) {}
	}

	public MySql(String url, String db, String name, String pass) {
		this(url + "/" + db, name, pass);
	}

	public MySql(String url, String name, String pass) {
		this.url = url;
		this.name = name;
		this.pass = pass;
	}

	public Connection createConnection() throws SQLException {
		return DriverManager.getConnection("jdbc:mysql://" + url + "?characterEncoding=UTF-8", name, pass);
	}
}
