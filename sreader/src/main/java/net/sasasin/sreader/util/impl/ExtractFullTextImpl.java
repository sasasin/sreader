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
package net.sasasin.sreader.util.impl;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import net.sasasin.sreader.dao.EftRulesDao;
import net.sasasin.sreader.orm.EftRules;
import net.sasasin.sreader.util.ExtractFullText;

import com.gargoylesoftware.htmlunit.StringWebResponse;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HTMLParser;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class ExtractFullTextImpl implements ExtractFullText {

	private EftRulesDao eftRulesDao = null;

	public ExtractFullTextImpl(EftRulesDao eftRulesDao){
		this.eftRulesDao = eftRulesDao;
	}
	
	@Override
	public String analyse(String html, URL url) {
		String result = null;
		HtmlPage h = null;
		try {
			h = getHtmlPage(html, url);
		} catch (IOException e) {
			e.printStackTrace();
		}
		// 本文抽出のルール取得
		EftRules eftRules = eftRulesDao.getByUrl(url);
		String xpath = "";
		if (eftRules != null) {
			xpath = eftRules.getExtractRule();
		}
		// xpath.isEmptyだとHtmlUnitが例外を投げて面倒くさい
		if (!xpath.isEmpty()) {
			// xpathで複数抽出されるのもある
			@SuppressWarnings("unchecked")
			List<HtmlElement> bodys = (List<HtmlElement>) h.getByXPath(xpath);
			if (!bodys.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				for (HtmlElement body : bodys) {
					sb.append(body.asText() + '\n');
				}
				result = sb.toString();
			}
		}
		// 結局取れなければ全部入れる
		if (result == null) {
			result = h.getBody().asText();
		}
		return result;
	}

	private HtmlPage getHtmlPage(String html, URL url) throws IOException {
		WebClient c = new WebClient();
		c.setCssEnabled(false);
		c.setAppletEnabled(false);
		c.setActiveXNative(false);
		c.setJavaScriptEnabled(false);
		c.setPopupBlockerEnabled(true);

		HtmlPage h = HTMLParser.parseHtml(new StringWebResponse(html, "UTF-8",
				url), c.getCurrentWindow());
		return h;
	}
}
