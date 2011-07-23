/**
 * 
 */
package net.sasasin.sreader.util;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.commons.io.FileUtils;
import org.h2.tools.Server;

/**
 * @author sasasin
 * 
 */
public class DbUtil {

	private static Server server;
	private static String[] serverArgs;

	static {
		serverArgs = new String[] { "-baseDir", "~/h2datafiles", "-web",
				"-tcp", "tcpAllowOthers", "true" };
	}

	public static boolean startServer() {
		try {
			if (server == null) {
				server = Server.createTcpServer(serverArgs).start();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return server.isRunning(false);
	}

	public static boolean stopServer(Connection conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
			}
		}
		server.stop();
		return server.isRunning(false);
	}

	public static Connection getConnection() throws SQLException {

		try {
			FileUtils.forceMkdir(new File(System.getProperty("user.home")
					+ "/h2datafiles"));
		} catch (IOException e) {
		}
		startServer();
		Connection conn;
		conn = DriverManager.getConnection(
				"jdbc:h2:tcp://localhost/~/h2datafiles/sreader", "sa", "");
		// close時の自動コミット防止
		conn.setAutoCommit(false);
		return conn;
	}

}
