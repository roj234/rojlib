package roj.sql;

import roj.util.EmptyArrays;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * @author Roj233
 * @since 2021/8/3 14:58
 */
public class DbStatement {
	private final String query;
	private final Object[] values;

	public DbStatement(String query) {
		this.query = query;
		this.values = EmptyArrays.OBJECTS;
	}

	public DbStatement(String query, Object... values) {
		this.query = query;
		this.values = values;
	}

	public PreparedStatement prepareStatement(Connection con) throws SQLException {
		PreparedStatement ps = con.prepareStatement(query);
		for (int i = values.length; i > 0; ) {
			ps.setObject(i--, values[i]);
		}
		return ps;
	}

	public String toString() {return "Statement: " + query + ", " + Arrays.toString(values);}
}