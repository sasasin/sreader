/**
 * 
 */
package net.sasasin.sreader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.sasasin.sreader.ormap.FeedUrl;
import net.sasasin.sreader.util.DbUtil;

/**
 * @author sasasin
 *
 */
public class FeedUrlDriver {

	private File path;

	public FeedUrlDriver(File path){
		setPath(path);
	}

	public File getPath() {
		return path;
	}
		
	public void importFeedUrl(List<FeedUrl> ful) {
		Connection conn = null;
		try {
			conn = DbUtil.getConnection();
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

	public void run(){
		List<FeedUrl> ful = this.textFileToList(path);
		this.importFeedUrl(ful);
	}
	private void setPath(File path) {
		this.path = path;
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

}