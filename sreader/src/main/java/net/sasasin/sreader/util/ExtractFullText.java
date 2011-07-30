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
package net.sasasin.sreader.util;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import net.sasasin.sreader.orm.EftRules;

import org.hibernate.Session;
import org.hibernate.Transaction;

import com.gargoylesoftware.htmlunit.StringWebResponse;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HTMLParser;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class ExtractFullText {
	public String analyse(String html, URL url) {
		// title、bodyを詰める
		String result = null;

		try {
			WebClient c = new WebClient();
			c.setCssEnabled(false);
			c.setAppletEnabled(false);
			c.setActiveXNative(false);
			c.setJavaScriptEnabled(false);
			c.setPopupBlockerEnabled(true);
			HtmlPage h = HTMLParser.parseHtml(new StringWebResponse(html,
					"UTF-8", url), c.getCurrentWindow());

			// 本文抽出のルール取得
			String xpath = getExtractRule(url);
			@SuppressWarnings("unchecked")
			List<HtmlElement> bodys = (List<HtmlElement>) h.getByXPath(xpath);
			if (!bodys.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				for (HtmlElement body : bodys) {
					sb.append(body.asText() + '\n');
				}
				result = sb.toString();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
		}
		// 結局取れなければ全部入れる
		if (result == null) {
			result = html;
		}
		return result;
	}

	public String getExtractRule(URL url) {
		String extractRule = null;
		String sql = "select e.* from eft_rules e where :url regexp url order by length(url) desc";

		Session ses = DbUtil.getSessionFactory().openSession();
		Transaction tx = ses.beginTransaction();

		EftRules er = (EftRules) ses.createSQLQuery(sql)
				.addEntity(EftRules.class).setParameter("url", url.toString())
				.setMaxResults(1).uniqueResult();
		if (er != null) {
			extractRule = er.getExtractRule();
		} else {
			extractRule = "";
		}
		tx.commit();
		return extractRule;
	}

}
