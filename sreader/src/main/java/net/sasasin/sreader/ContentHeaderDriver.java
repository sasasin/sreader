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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.sasasin.sreader.ormap.ContentHeader;
import net.sasasin.sreader.ormap.FeedUrl;
import net.sasasin.sreader.util.DbUtil;
import net.sasasin.sreader.util.Md5Util;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author sasasin
 * 
 */
public class ContentHeaderDriver {

	// get contents from url
	// parse feed
	// return list
	public Set<ContentHeader> fetch(String urlstr) {
		Set<ContentHeader> c = new HashSet<ContentHeader>();
		try {
			// URLからいきなりDocument
			Document d = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder().parse(urlstr);
			// RSS
			parseFeed(urlstr, c, d.getElementsByTagName("item"));
			// RSS2.0,Atom
			parseFeed(urlstr, c, d.getElementsByTagName("entry"));
		} catch (IOException e) {
		} catch (SAXException e) {
		} catch (ParserConfigurationException e) {
		}
		return c;
	}

	public Set<FeedUrl> getFeedUrl() {
		Set<FeedUrl> s = new HashSet<FeedUrl>();

		Connection conn = null;
		try {
			conn = DbUtil.getConnection();
			PreparedStatement sel = conn
					.prepareStatement("select url, auth_name, auth_password, account_id from feed_url");
			sel.execute();
			ResultSet rs = sel.getResultSet();
			while (rs.next()) {
				s.add(new FeedUrl(rs.getString(1), rs.getString(2), rs
						.getString(3), rs.getString(4)));
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (conn != null) {
				DbUtil.stopServer(conn);
			}
		}

		return s;
	}

	public void importContentHeader(Set<ContentHeader> chs) {
		Connection conn = null;
		try {
			conn = DbUtil.getConnection();
			// 重複チェック用SQL
			PreparedStatement sel = conn
					.prepareStatement("select count(*) from content_header where id = ?");
			// 投入用SQL
			PreparedStatement up = conn
					.prepareStatement("insert into content_header(id, url, title, feed_url_id) values(?, ?, ?, ?)");

			for (ContentHeader ch : chs) {
				// 投入前に重複チェック
				sel.setString(1, ch.getId());
				ResultSet rs = sel.executeQuery();
				rs.next();
				if (rs.getInt(1) < 1) {
					// キーで探して居なければ投入
					up.setString(1, ch.getId());
					up.setString(2, ch.getUrl());
					up.setString(3, ch.getTitle());
					up.setString(4, ch.getFeedUrlId());
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

	private void parseFeed(String urlstr, Set<ContentHeader> c, NodeList nl) {
		for (int i = 0; i < nl.getLength(); i++) {
			String title = null;
			String url = null;
			Node n = nl.item(i);
			NodeList nlist = n.getChildNodes();
			try {
				// 1st try
				url = n.getAttributes().getNamedItem("rdf:about")
						.getNodeValue();
			} catch (NullPointerException e) {
				// no action
			}
			for (int j = 0; j < nlist.getLength(); j++) {
				Node child = nlist.item(j);
				if (child.getNodeName().equals("title")) {
					title = child.getTextContent().trim();
				} else if (child.getNodeName().equals("link")) {
					if (url == null || url.length() == 0) {
						// 2nd try
						url = child.getTextContent().trim();
					}
					if (url == null || url.length() == 0) {
						try {
							// 3rd try
							url = child.getAttributes().getNamedItem("href")
									.getNodeValue();
						} catch (NullPointerException e) {
							// no action
						}
					}
				}
				if (title != null && url != null) {
					c.add(new ContentHeader(url, title, Md5Util.crypt(urlstr)));
				}
			}
		}
	}

	public void run() {

		for (FeedUrl fu : getFeedUrl()) {
			// RSS/Atom feed to Set<....>
			Set<ContentHeader> s = this.fetch(fu.getUrl());
			this.importContentHeader(s);
		}
	}

}
