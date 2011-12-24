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

import java.net.MalformedURLException;
import java.net.URL;

import net.sasasin.sreader.dao.ContentFullTextDao;
import net.sasasin.sreader.dao.ContentHeaderDao;
import net.sasasin.sreader.dao.LoginRulesDao;
import net.sasasin.sreader.dao.SubscriberDao;
import net.sasasin.sreader.dao.impl.ContentFullTextDaoHibernateImpl;
import net.sasasin.sreader.dao.impl.ContentHeaderDaoHibernateImpl;
import net.sasasin.sreader.dao.impl.LoginRulesDaoHibernateImpl;
import net.sasasin.sreader.dao.impl.SubscriberDaoHibernateImpl;
import net.sasasin.sreader.orm.ContentFullText;
import net.sasasin.sreader.orm.ContentHeader;
import net.sasasin.sreader.orm.FeedUrl;
import net.sasasin.sreader.orm.LoginRules;
import net.sasasin.sreader.orm.Subscriber;
import net.sasasin.sreader.util.Md5Util;
import net.sasasin.sreader.util.Wget;
import net.sasasin.sreader.util.impl.ExtractFullTextImpl;
import net.sasasin.sreader.util.impl.WgetImpl;

public class ContentFullTextDriver {

	private LoginRulesDao loginRulesDao = new LoginRulesDaoHibernateImpl();
	private SubscriberDao subscriberDao = new SubscriberDaoHibernateImpl();
	private ContentHeaderDao contentHeaderDao = new ContentHeaderDaoHibernateImpl();
	private ContentFullTextDao contentFullTextDao = new ContentFullTextDaoHibernateImpl();

	public ContentFullText fetch(ContentHeader ch) {
		ContentFullText c = null;
		try {

			FeedUrl f = ch.getFeedUrl();
			// ログインIDとパスワードはSubscriberにある。
			Subscriber sub = subscriberDao.getByFeedUrl(f);

			Wget w = new WgetImpl();
			w.setUrl(new URL(ch.getUrl()));
			// ログインIDとパスワードがあれば
			if (sub != null) {
				// ログイン情報も取ってきて
				LoginRules lr = loginRulesDao
						.getByHostname(new URL(ch.getUrl()).getHost());
				w.setLoginInfo(lr);
				w.setLoginId(sub.getAuthName());
				w.setLoginPassword(sub.getAuthPassword());
			}
			// Wget.read()は、任意の文字コードからUTF-8に変換し、Stringに詰める。
			String s = w.read();
			// Stringの文字コードと、HTMLのcharsetに記載された文字コード名が一致していないと
			// HtmlUnitがパニックを起こす。その対策。
			s = s.replaceAll("charset=(.*?)\"", "charset=UTF-8\"");
			if (s.length() > 0) {

				c = new ContentFullText();
				c.setId(Md5Util.crypt(ch.getUrl()));
				c.setFullText(new ExtractFullTextImpl().analyse(s,
						new URL(ch.getUrl())));
				c.setContentHeader(ch);

			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return c;

	}

	private void importContentFullText(ContentFullText s) {
		ContentFullText s2 = contentFullTextDao.get(s.getId());
		if (s2 == null) {
			contentFullTextDao.save(s);
		}
	}

	public void run() {

		for (ContentHeader ch : contentHeaderDao
				.findByConditionOfFullTextNotFetched()) {
			ContentFullText s = this.fetch(ch);
			if (s != null) {
				this.importContentFullText(s);
			}
		}
	}

	public static void main(String[] args) {
		// get content full text.
		new ContentFullTextDriver().run();
	}
}
