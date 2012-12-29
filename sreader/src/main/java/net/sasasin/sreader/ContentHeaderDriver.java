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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sasasin.sreader.dao.ContentHeaderDao;
import net.sasasin.sreader.dao.FeedUrlDao;
import net.sasasin.sreader.dao.impl.ContentHeaderDaoHibernateImpl;
import net.sasasin.sreader.dao.impl.FeedUrlDaoHibernateImpl;
import net.sasasin.sreader.orm.ContentHeader;
import net.sasasin.sreader.orm.FeedUrl;
import net.sasasin.sreader.util.Md5Util;
import net.sasasin.sreader.util.impl.WgetHttpComponentsImpl;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

/**
 * @author sasasin
 * 
 */
public class ContentHeaderDriver {
	private static Logger logger = LoggerFactory.getLogger("net.sasasin.sreader");

	private FeedUrlDao feedUrlDao = new FeedUrlDaoHibernateImpl();
	private ContentHeaderDao contentHeaderDao = new ContentHeaderDaoHibernateImpl();

	/**
	 * FeedUrlで表されるRSSを取得し、ContentHeaderのリストを返す。
	 * 
	 * @param f
	 * @return
	 */
	public Set<ContentHeader> fetch(FeedUrl f) {
		Set<ContentHeader> c = new HashSet<ContentHeader>();
		fetchByRome(f, c);
		return c;
	}

	@SuppressWarnings("unchecked")
	private void fetchByRome(FeedUrl f, Set<ContentHeader> c) {
		try {
			// 文字化けるRSS対策
			InputStream is = IOUtils
					.toInputStream(new WgetHttpComponentsImpl(new URL(f.getUrl())).read());
			// Romeパーサー
			SyndFeed feed = new SyndFeedInput().build(new XmlReader(is));
			for (SyndEntry entry : (List<SyndEntry>) feed.getEntries()) {
				
				logger.info(this.getClass().getSimpleName() + " processing " + entry.getLink());
				
				ContentHeader ch = new ContentHeader();

				// HTTP 30x対策。moved先のURLを取得する。
				// 30xしていなければ、new URL(entry.getLink())したものが返る。
				URL entryUrl = new WgetHttpComponentsImpl(new URL(entry.getLink()))
						.getOriginalUrl();

				ch.setUrl(entryUrl.toString());
				ch.setId(Md5Util.crypt(ch.getUrl()));
				ch.setTitle(entry.getTitle());
				ch.setFeedUrl(f);
				c.add(ch);
			}

		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (FeedException e) {
			e.printStackTrace();
		}

	}

	public void importContentHeader(Set<ContentHeader> chs) {
		for (ContentHeader ch : chs) {
			// 投入前に重複チェック
			ContentHeader ch2 = contentHeaderDao.get(ch.getId());
			if (ch2 == null) {
				// キーで探して居なければ投入
				contentHeaderDao.save(ch);
			}
		}
	}

	public void run() {
		logger.info(this.getClass().getSimpleName() +" is started.");
		
		for (FeedUrl fu : feedUrlDao.findAll()) {
			// RSS/Atom feed to Set<....>
			Set<ContentHeader> s = this.fetch(fu);
			this.importContentHeader(s);
		}
		
		logger.info(this.getClass().getSimpleName() +" is ended.");
	}

	public static void main(String[] args) {
		// import RSS/Atom to content_header table.
		new ContentHeaderDriver().run();
	}

}
