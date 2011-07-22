package net.sasasin.sreader;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import net.sasasin.sreader.ormap.ContentFullText;
import net.sasasin.sreader.ormap.ContentHeader;
import net.sasasin.sreader.ormap.LoginUrl;
import net.sasasin.sreader.util.DbUtil;
import net.sasasin.sreader.util.ExtractContent;
import net.sasasin.sreader.util.Md5Util;
import net.sasasin.sreader.util.Wget;

public class ContentFullTextDriver {

	public ContentFullText fetch(String url, String feedUrlId) {
		ContentFullText c = null;
		String s = null;
		String[] auth = null;
		try {
			auth = getAuthInfo(feedUrlId);
			if (auth != null && auth[0] != null && auth[1] != null) {
				LoginUrl l = getLoginUrl(new URL(url).getHost());
				s = new Wget(new URL(url)).read(l, auth[0], auth[1]);
			} else {
				s = new Wget(new URL(url)).read();
			}
			if (s.length() > 0) {
				c = new ContentFullText(new ExtractContent().analyse(s).get("body"), Md5Util.crypt(url));
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return c;

	}

	public LoginUrl getLoginUrl(String hostName) {
		LoginUrl l = null;
		Connection conn = null;
		try {
			conn = DbUtil.getConnection();
			// fetch full text is null records.
			PreparedStatement sel = conn
					.prepareStatement("select post_url, id_box_name, password_box_name"
							+ " from login_url where host_name = ?");
			sel.setString(1, hostName);
			sel.execute();
			ResultSet rs = sel.getResultSet();
			rs.next();
			l = new LoginUrl(hostName, rs.getString(1), rs.getString(2),
					rs.getString(3));
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
		return l;
	}

	public String[] getAuthInfo(String feedUrlId) {
		String[] s = new String[2];
		Connection conn = null;
		try {
			conn = DbUtil.getConnection();
			// fetch full text is null records.
			PreparedStatement sel = conn
					.prepareStatement("select auth_name, auth_password"
							+ " from feed_url where id = ?");
			sel.setString(1, feedUrlId);
			sel.execute();
			ResultSet rs = sel.getResultSet();
			rs.next();
			s[0] = rs.getString(1);
			s[1] = rs.getString(2);
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
		return s;
	}

	public Set<ContentHeader> getContentHeader() {
		Set<ContentHeader> s = new HashSet<ContentHeader>();

		Connection conn = null;
		try {
			conn = DbUtil.getConnection();
			// fetch full text is null records.
			PreparedStatement sel = conn
					.prepareStatement("select h.url, h.title, h.feed_url_id"
							+ " from content_header h left outer join content_full_text f"
							+ " on h.id = f.content_header_id where f.id is null");
			sel.execute();
			ResultSet rs = sel.getResultSet();
			while (rs.next()) {
				s.add(new ContentHeader(rs.getString(1), rs.getString(2), rs
						.getString(3)));
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

		return s;

	}

	private void importContentFullText(ContentFullText s) {
		// TODO Auto-generated method stub
		Connection conn = null;

		try {
			conn = DbUtil.getConnection();
			// 重複チェック用SQL
			PreparedStatement sel = conn
					.prepareStatement("select count(*) from content_full_text where id = ?");
			// 投入用SQL
			PreparedStatement up = conn
					.prepareStatement("insert into content_full_text(id, full_text, content_header_id) values(?, ?, ?)");

			// 投入前に重複チェック
			sel.setString(1, s.getId());
			ResultSet rs = sel.executeQuery();
			rs.next();
			if (rs.getInt(1) < 1) {
				// キーで探して居なければ投入
				up.setString(1, s.getId());
				up.setString(2, s.getFullText());
				up.setString(3, s.getContentHeaderId());
				up.executeUpdate();
			}
			rs.close();
			conn.commit();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
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
	}

	public void run() {

		for (ContentHeader ch : getContentHeader()) {
			ContentFullText s = this.fetch(ch.getUrl(), ch.getFeedUrlId());
			if (s != null) {
				this.importContentFullText(s);
			}
		}
	}

}
