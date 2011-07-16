/**
 * 
 */
package net.sasasin.sreader.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author sasasin
 *
 */
public class DbUtil {
	public static Connection getConnection() throws SQLException {
		Connection conn;
		conn = DriverManager.getConnection(
				"jdbc:h2:tcp://localhost/~/h2datafiles/test", "sa", "");
		// close時の自動コミット防止
		conn.setAutoCommit(false);
		
		return conn;
	}

}
