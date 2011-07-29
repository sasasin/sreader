/*
 * SReader is RSS/Atom feed reader with full text.
 *
 * Copyright (C) 2011, Shinnosuke Suzuki <sasasin@sasasin.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *	
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
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
					.prepareStatement("insert into feed_url(id, url, auth_name, auth_password, account_id) values(?, ?, ?, ?, ?)");

			for (FeedUrl fu : ful) {
				// 投入前に重複チェック
				sel.setString(1, fu.getId());
				ResultSet rs = sel.executeQuery();
				rs.next();
				if (rs.getInt(1) < 1) {
					// キーで探して居なければ投入
					up.setString(1, fu.getId());
					up.setString(2, fu.getUrl());
					up.setString(3, fu.getAuthId());
					up.setString(4, fu.getAuthPassword());
					up.setString(5, "hoge");
					up.executeUpdate();
				}
				rs.close();
			}
			conn.commit();

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (conn != null) {
				DbUtil.stopServer(conn);
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
				if (s.length == 3 ){
					fu.add(new FeedUrl(s[0], s[1], s[2], "hoge"));
				}else{
					fu.add(new FeedUrl(s[0]));					
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return fu;
	}

}
