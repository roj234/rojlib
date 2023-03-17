package roj.sql;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author Roj233
 * @since 2021/8/3 15:26
 */
public final class SQLite extends DbConnector {
	static {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private final File db;

	public SQLite(File db) {
		this.db = db;
	}

	@Override
	public Connection createConnection() throws SQLException {
		return DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
	}
}
