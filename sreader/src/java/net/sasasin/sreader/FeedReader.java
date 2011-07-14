/**
 * 
 */
package net.sasasin.sreader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.sasasin.sreader.ormap.ContentHeader;
import net.sasasin.sreader.ormap.FeedUrl;

/**
 * @author sasasin
 * 
 */
public class FeedReader {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		if (args.length < 1) {
			return;
		}

		File path = new File(args[0]);
		if (!path.exists() || !path.isFile() || !path.canRead()) {
			return;
		}
		FeedReader fr = new FeedReader();
		List<FeedUrl> fu = fr.textFileToList(path);
		fr.importFeedUrl(fu);
	}

	public List<FeedUrl> textFileToList(File path) {
		List<FeedUrl> fu = new ArrayList<FeedUrl>();
		try {
			BufferedReader r = new BufferedReader(new FileReader(path));
			while (r.ready()) {
				String[] s = r.readLine().split("\t");
				fu.add(new FeedUrl(s[0]));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return fu;
	}

	public void importFeedUrl(List<FeedUrl> ful) {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(
					"jdbc:h2:tcp://localhost/~/h2datafiles/test", "sa", "");
			// close時の自動コミット防止
			conn.setAutoCommit(false);
			// 重複チェック用SQL
			PreparedStatement sel = conn
					.prepareStatement("select count(*) from feed_url where id = ?");
			// 投入用SQL
			PreparedStatement up = conn
					.prepareStatement("insert into feed_url(id, url, account_id) values(?, ?, ?)");

			for (FeedUrl fu : ful) {
				// 投入前に重複チェック
				sel.setString(1, fu.getId());
				ResultSet rs = sel.executeQuery();
				rs.next();
				if (rs.getInt(1) < 1) {
					// キーで探して居なければ投入
					up.setString(1, fu.getId());
					up.setString(2, fu.getUrl());
					up.setString(3, "hoge");
					up.executeUpdate();
				}
				rs.close();
			}
			conn.commit();

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

	}

	public void importContentHeader(List<ContentHeader> chl){
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(
					"jdbc:h2:tcp://localhost/~/h2datafiles/test", "sa", "");
			// close時の自動コミット防止
			conn.setAutoCommit(false);
			// 重複チェック用SQL
			PreparedStatement sel = conn
					.prepareStatement("select count(*) from content_header where id = ?");
			// 投入用SQL
			PreparedStatement up = conn
					.prepareStatement("insert into content_header(id, url, feed_url_id) values(?, ?, ?)");

			for (ContentHeader ch : chl) {
				// 投入前に重複チェック
				sel.setString(1, ch.getId());
				ResultSet rs = sel.executeQuery();
				rs.next();
				if (rs.getInt(1) < 1) {
					// キーで探して居なければ投入
					up.setString(1, ch.getId());
					up.setString(2, ch.getUrl());
					up.setString(3, ch.getFeedUrlId());
					up.executeUpdate();
				}
				rs.close();
			}
			conn.commit();

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
	}
	
}
