package net.sasasin.sreader.publish;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.sasasin.sreader.util.DbUtil;

public abstract class AbstractPublisher {

	public void run() {
		ArrayList<Map<String, String>> contents = getContent();
		init();
		for (Map<String, String> content : contents) {
			publish(content);
		}
		finalize();
	}

	public void init() {
	}

	public void finalize() {
	}

	public void publish(Map<String, String> content) {
	}

	public ArrayList<Map<String, String>> getContent() {
		ArrayList<Map<String, String>> contents = new ArrayList<Map<String, String>>();
		Map<String, String> content = null;

		Connection conn = null;
		try {
			conn = DbUtil.getConnection();

			PreparedStatement sql = conn
					.prepareStatement("select id, url, title, full_text from content_view order by url");
			sql.execute();
			ResultSet rs = sql.getResultSet();
			while (rs.next()) {
				content = new HashMap<String, String>();
				content.put("id", rs.getString(1));
				content.put("url", rs.getString(2));
				content.put("title", rs.getString(3));
				content.put("full_text", rs.getString(4));
				contents.add(content);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (conn != null) {
				DbUtil.stopServer(conn);
			}

		}
		return contents;
	}

	public void log(Map<String, String> content) {
		Connection conn = null;
		try {
			conn = DbUtil.getConnection();

			PreparedStatement sql = conn
					.prepareStatement("insert into publish_log(content_header_id) values(?)");
			sql.setString(1, content.get("id"));
			sql.execute();
			sql.close();
			conn.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (conn != null) {
				DbUtil.stopServer(conn);
			}

		}
	}

}
