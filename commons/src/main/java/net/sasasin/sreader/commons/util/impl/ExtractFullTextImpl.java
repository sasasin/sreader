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
package net.sasasin.sreader.commons.util.impl;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.PatternSyntaxException;

import net.sasasin.sreader.commons.entity.EftRules;
import net.sasasin.sreader.commons.util.ExtractFullText;

import com.gargoylesoftware.htmlunit.StringWebResponse;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.html.HTMLParser;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class ExtractFullTextImpl implements ExtractFullText {

	private List<EftRules> eftRulesList;

	public ExtractFullTextImpl(List<EftRules> eftRulesList) {
		this.eftRulesList = eftRulesList;
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
		EftRules eftRules = getEftRulesByUrl(url);
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
		WebClientOptions copt =c.getOptions();
		copt.setCssEnabled(false);
		copt.setAppletEnabled(false);
		copt.setActiveXNative(false);
		copt.setJavaScriptEnabled(false);
		copt.setPopupBlockerEnabled(true);

		HtmlPage h = HTMLParser.parseHtml(new StringWebResponse(html, "UTF-8",
				url), c.getCurrentWindow());
		return h;
	}

	/**
	 * URLに使用可能と考えられるEftRulesを取得する。
	 * 使用可能と考えられるものがない場合は、空EftRulesを返す。
	 * 
	 * @param url
	 * @return
	 */
	private EftRules getEftRulesByUrl(URL url) {
		EftRules result = new EftRules("","","");
		String urlstr = url.toString();
		List<CallableTask> tasks = new ArrayList<CallableTask>();
		for (EftRules er : eftRulesList) {
			tasks.add(new CallableTask(urlstr, er));
		}
		ExecutorService ex = Executors.newCachedThreadPool();
		try {
			for (Future<EftRules> f : ex.invokeAll(tasks)) {
				EftRules r = f.get();
				if (r != null) {
					if (result.getUrl().length() < r.getUrl().length()) {
						result = r;
					}
				}
			}
			ex.shutdown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}

		return result;
	}

	private class CallableTask implements Callable<EftRules> {
		private String urlstr;
		private EftRules er;

		public CallableTask(String urlstr, EftRules er) {
			this.urlstr = urlstr;
			this.er = er;
		}

		@Override
		public EftRules call() throws Exception {
			try {
				if (urlstr.matches(er.getUrl() + ".*")) {
					return er;
				}
			} catch (PatternSyntaxException e) {
				// たまに、JavaのRegex構文エラーとなるEftRulesがいて、この例外が出る
				return null;
			}
			return null;
		}

	}
}
