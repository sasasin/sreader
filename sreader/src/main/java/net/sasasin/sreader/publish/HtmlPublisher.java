package net.sasasin.sreader.publish;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.sasasin.sreader.util.DbUtil;

public class HtmlPublisher {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		new HtmlPublisher().run();
	}

	public void run() {
		ArrayList<Map<String, String>> contents = getContent();
		init();
		for (Map<String, String> content : contents) {
			publish(content);
		}
		finalize();
	}

	public void init() {
		System.out.println("<html>");
		System.out.println("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" /></head>");
		System.out.println("<body>");
	}

	public void finalize() {
		System.out.println("</body><html>");
	}

	public void publish(Map<String, String> content) {
		System.out.println("<hr>");
		// title with url
		System.out.println("<h1><a href='" + content.get("url") + "'>"
				+ content.get("title") + "</a></h1>");
		System.out.println("<p>");
		// content full text
		System.out.println(content.get("full_text").replaceAll("(?m)^", "<p>"));
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
				try {
					conn.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
		return contents;
	}
}
