package roj.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author Roj233
 * @since 2021/8/3 15:26
 */
public final class MariaDB extends DbConnector {
	private final String name, pass, url;

	static {
		try {
			Class.forName("org.mariadb.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public MariaDB(String url, String db, String user, String pass) {
		this(url + "/" + db, user, pass);
	}

	public MariaDB(String url, String name, String pass) {
		this.url = url;
		this.name = name;
		this.pass = pass;
	}

	public Connection createConnection() throws SQLException {
		return DriverManager.getConnection("jdbc:mariadb://" + url + "?characterEncoding=UTF-16LE", name, pass);
	}
}
