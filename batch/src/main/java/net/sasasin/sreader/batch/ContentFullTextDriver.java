/*
 * SReader is RSS/Atom feed reader with full text.
 *
 * Copyright (C) 2011-2013, Shinnosuke Suzuki <sasasin@sasasin.net>
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
package net.sasasin.sreader.batch;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import net.sasasin.sreader.commons.dao.ContentFullTextDao;
import net.sasasin.sreader.commons.dao.ContentHeaderDao;
import net.sasasin.sreader.commons.dao.EftRulesDao;
import net.sasasin.sreader.commons.dao.LoginRulesDao;
import net.sasasin.sreader.commons.dao.SubscriberDao;
import net.sasasin.sreader.commons.dao.impl.ContentFullTextDaoHibernateImpl;
import net.sasasin.sreader.commons.dao.impl.ContentHeaderDaoHibernateImpl;
import net.sasasin.sreader.commons.dao.impl.EftRulesDaoHibernateImpl;
import net.sasasin.sreader.commons.dao.impl.LoginRulesDaoHibernateImpl;
import net.sasasin.sreader.commons.dao.impl.SubscriberDaoHibernateImpl;
import net.sasasin.sreader.commons.entity.ContentFullText;
import net.sasasin.sreader.commons.entity.ContentHeader;
import net.sasasin.sreader.commons.entity.EftRules;
import net.sasasin.sreader.commons.entity.FeedUrl;
import net.sasasin.sreader.commons.entity.LoginRules;
import net.sasasin.sreader.commons.entity.Subscriber;
import net.sasasin.sreader.commons.util.Md5Util;
import net.sasasin.sreader.commons.util.Wget;
import net.sasasin.sreader.commons.util.impl.ExtractFullTextImpl;
import net.sasasin.sreader.commons.util.impl.WgetHtmlUnitImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContentFullTextDriver {
	private static Logger logger = LoggerFactory
			.getLogger("net.sasasin.sreader.batch");

	private LoginRulesDao loginRulesDao = new LoginRulesDaoHibernateImpl();
	private SubscriberDao subscriberDao = new SubscriberDaoHibernateImpl();
	private ContentHeaderDao contentHeaderDao = new ContentHeaderDaoHibernateImpl();
	private ContentFullTextDao contentFullTextDao = new ContentFullTextDaoHibernateImpl();
	private EftRulesDao eftRulesDao = new EftRulesDaoHibernateImpl();
	private List<EftRules> eftRulesList;

	public ContentFullText fetch(ContentHeader ch) {
		ContentFullText c = null;
		try {

			FeedUrl f = ch.getFeedUrl();
			// ログインIDとパスワードはSubscriberにある。
			Subscriber sub = subscriberDao.getByFeedUrl(f);

			Wget w = new WgetHtmlUnitImpl();
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
				c.setFullText(new ExtractFullTextImpl(eftRulesList).analyse(s,
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
		if (contentFullTextDao.get(s.getId()) == null) {
			contentFullTextDao.save(s);
		}
	}

	public void run() {
		logger.info(this.getClass().getSimpleName() + " is started.");

		eftRulesList = eftRulesDao.findAll();

		for (ContentHeader ch : contentHeaderDao
				.findByConditionOfFullTextNotFetched()) {

			logger.info(this.getClass().getSimpleName() + " fetch "
					+ ch.getUrl());

			ContentFullText s = this.fetch(ch);
			if (s != null) {
				this.importContentFullText(s);
			}
		}

		logger.info(this.getClass().getSimpleName() + " is ended.");
	}

	public static void main(String[] args) {
		// get content full text.
		new ContentFullTextDriver().run();
	}
}
