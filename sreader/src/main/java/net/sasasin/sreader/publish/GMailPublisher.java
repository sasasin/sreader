/**
 * 
 */
package net.sasasin.sreader.publish;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import net.sasasin.sreader.util.DbUtil;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

/**
 * @author sasasin
 * 
 */
public class GMailPublisher extends AbstractPublisher {

	private Email email = new SimpleEmail();

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new GMailPublisher().run();
	}

	public void init() {
		Map<String, String> m = getLoginInfo();
		email.setHostName("smtp.gmail.com");
		email.setSmtpPort(587);
		email.setAuthenticator(new DefaultAuthenticator(m.get("address"), m
				.get("password")));
		email.setTLS(true);
		email.setCharset("UTF-8");
		try {
			email.setFrom(m.get("address"));
			email.addTo(m.get("address"));
		} catch (EmailException e) {
			e.printStackTrace();
		}
	}

	public void publish(Map<String, String> content) {
		try {
			email.setSubject(content.get("title"));
			email.setMsg(content.get("url") + "\n" + content.get("full_text"));
			email.send();
			log(content);
		} catch (EmailException e) {
			e.printStackTrace();
		}
	}

	private Map<String, String> getLoginInfo() {
		Map<String, String> m = new HashMap<String, String>();
		Connection conn = null;
		try {
			conn = DbUtil.getConnection();

			PreparedStatement sql = conn
					.prepareStatement("select address, password from gmail_login_info");
			sql.execute();
			ResultSet rs = sql.getResultSet();
			rs.next();
			m = new HashMap<String, String>();
			m.put("address", rs.getString(1));
			m.put("password", rs.getString(2));
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (conn != null) {
				DbUtil.stopServer(conn);
			}
		}
		return m;
	}

}