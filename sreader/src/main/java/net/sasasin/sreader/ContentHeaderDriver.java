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
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.sasasin.sreader.orm.ContentHeader;
import net.sasasin.sreader.orm.FeedUrl;
import net.sasasin.sreader.util.DbUtil;
import net.sasasin.sreader.util.Md5Util;
import net.sasasin.sreader.util.Wget;

import org.apache.commons.io.IOUtils;
import org.hibernate.Session;
import org.hibernate.Transaction;
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
	public Set<ContentHeader> fetch(FeedUrl f) {
		Set<ContentHeader> c = new HashSet<ContentHeader>();
		try {
			//文字化けるRSS対策
			InputStream is = IOUtils.toInputStream(new Wget(new URL(f.getUrl())).readWithoutLogin());			
			Document d = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder().parse(is);
			// RSS
			parseFeed(f, c, d.getElementsByTagName("item"));
			// RSS2.0,Atom
			parseFeed(f, c, d.getElementsByTagName("entry"));
		} catch (IOException e) {
		} catch (SAXException e) {
		} catch (ParserConfigurationException e) {
		}
		return c;
	}

	public List<FeedUrl> getFeedUrl() {

		Session ses = DbUtil.getSessionFactory().openSession();
		Transaction tx = ses.beginTransaction();

		@SuppressWarnings("unchecked")
		List<FeedUrl> fs = ses.createCriteria(FeedUrl.class).list();
		tx.commit();

		return fs;
	}

	public void importContentHeader(Set<ContentHeader> chs) {
		Session ses = DbUtil.getSessionFactory().openSession();
		Transaction tx = ses.beginTransaction();
		for (ContentHeader ch : chs) {
			// 投入前に重複チェック
			ContentHeader ch2 = (ContentHeader) ses.get(ContentHeader.class,
					ch.getId());
			if (ch2 == null) {
				// キーで探して居なければ投入
				ses.save(ch);
			}
		}
		tx.commit();

	}

	private void parseFeed(FeedUrl f, Set<ContentHeader> c, NodeList nl) {
		ContentHeader ch = null;
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
					ch = new ContentHeader();
					ch.setUrl(url);
					ch.setId(Md5Util.crypt(ch.getUrl()));
					ch.setTitle(title);
					ch.setFeedUrl(f);
					c.add(ch);
				}
			}
		}
	}

	public void run() {

		for (FeedUrl fu : getFeedUrl()) {
			// RSS/Atom feed to Set<....>
			Set<ContentHeader> s = this.fetch(fu);
			this.importContentHeader(s);
		}
	}

}
