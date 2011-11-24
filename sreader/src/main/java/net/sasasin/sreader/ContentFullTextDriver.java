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
import java.util.List;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;

import net.sasasin.sreader.orm.ContentFullText;
import net.sasasin.sreader.orm.ContentHeader;
import net.sasasin.sreader.orm.FeedUrl;
import net.sasasin.sreader.orm.LoginRules;
import net.sasasin.sreader.orm.Subscriber;
import net.sasasin.sreader.util.DbUtil;
import net.sasasin.sreader.util.ExtractFullText;
import net.sasasin.sreader.util.Md5Util;
import net.sasasin.sreader.util.Wget;

public class ContentFullTextDriver {

	public ContentFullText fetch(ContentHeader ch) {
		ContentFullText c = null;
		String s = null;
		Session ses = DbUtil.getSessionFactory().openSession();
		try {

			FeedUrl f = ch.getFeedUrl();
			// ログインIDとパスワードはSubscriberにある。
			// TODO 「有効なアカウントの」という条件を付与する。
			// TODO 「有効なアカウント」をチェックする仕組みを作る。
			Subscriber sub = (Subscriber) ses.createCriteria(Subscriber.class)
					.add(Restrictions.eq("feedUrl", f))
					.add(Restrictions.isNotNull("authName"))
					.add(Restrictions.isNotNull("authPassword"))
					.setMaxResults(1).uniqueResult();

			Wget w = new Wget(new URL(ch.getUrl()));
			// ログインIDとパスワードがあれば
			if (sub != null) {
				// ログイン情報も取ってきて
				w.setLoginInfo(getLoginRules(new URL(ch.getUrl()).getHost()));
				w.setLoginId(sub.getAuthName());
				w.setLoginPassword(sub.getAuthPassword());
			}
			// Wget.read()は、任意の文字コードからUTF-8に変換し、Stringに詰める。
			s = w.read();
			// Stringの文字コードと、HTMLのcharsetに記載された文字コード名が一致していないと
			// HtmlUnitがパニックを起こす。その対策。
			s = s.replaceAll("charset=(.*?)\"", "charset=UTF-8\"");
			if (s.length() > 0) {

				c = new ContentFullText();
				c.setId(Md5Util.crypt(ch.getUrl()));
				c.setFullText(new ExtractFullText().analyse(s, new URL(ch.getUrl())));
				c.setContentHeader(ch);

			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return c;

	}

	public LoginRules getLoginRules(String hostName) {

		LoginRules lr = null;
		Session ses = DbUtil.getSessionFactory().openSession();
		Transaction tx = ses.beginTransaction();

		lr = (LoginRules) ses
				.createQuery(
						"from LoginRules lr where lr.hostName = :p_hostName")
				.setParameter("p_hostName", hostName).setMaxResults(1)
				.uniqueResult();

		tx.commit();
		return lr;
	}

	public List<ContentHeader> getContentHeader() {

		Session ses = DbUtil.getSessionFactory().openSession();
		Transaction tx = ses.beginTransaction();

		// 本文未取得で、未配信のもの
		//TODO これでは、誰か一人でも配信されてたら、金輪際配信されなくなる
		//TODO 正しくは、誰か一人でも配信されていなければ、取得を試みるようにしないと
		String queryString = "select h.*"
				+ " from content_header h left outer join content_full_text f"
				+ " on h.id = f.content_header_id"
				+ " inner join feed_url fu"
				+ " on h.feed_url_id = fu.id"
				+ " where f.id is null"
				+ " and h.id not in (select content_header_id from publish_log)";
		@SuppressWarnings("unchecked")
		List<ContentHeader> l = (List<ContentHeader>) ses
				.createSQLQuery(queryString).addEntity(ContentHeader.class)
				.list();
		tx.commit();

		return l;
	}

	private void importContentFullText(ContentFullText s) {

		Session ses = DbUtil.getSessionFactory().openSession();
		Transaction tx = ses.beginTransaction();

		ContentFullText s2 = (ContentFullText) ses
				.createQuery("from ContentFullText where id = :id")
				.setParameter("id", s.getId()).uniqueResult();
		if (s2 == null) {
			ses.save(s);
		}
		tx.commit();
	}

	public void run() {

		for (ContentHeader ch : getContentHeader()) {
			ContentFullText s = this.fetch(ch);
			if (s != null) {
				this.importContentFullText(s);
			}
		}
	}

	public static void main(String[] args){
		// get content full text.
		new ContentFullTextDriver().run();
	}
}
