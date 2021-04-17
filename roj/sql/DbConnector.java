package roj.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author Roj233
 * @since 2021/8/8 15:33
 */
public abstract class DbConnector {
	protected Connection conn;

	public Connection getConnection() throws SQLException {
		if (conn != null && !conn.isClosed() && conn.isValid(2)) {
			return this.conn;
		}
		return conn = createConnection();
	}

	public abstract Connection createConnection() throws SQLException;

	public void flush(DbStatement statement) throws SQLException {
		PreparedStatement ps = statement.prepareStatement(getConnection());
		ps.executeUpdate();
		ps.close();
	}

	public void close() {
		if (conn != null) {
			try {
				conn.close();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		conn = null;
	}
}
