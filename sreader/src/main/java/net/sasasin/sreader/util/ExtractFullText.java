package net.sasasin.sreader.util;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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
		Connection conn = null;
		try {
			conn = DbUtil.getConnection();
			// 複数いた場合はURL文字列の長いルールを使用する
			PreparedStatement sel = conn
					.prepareStatement("select extract_rule from eft_rules where ? regexp url order by length(url) desc");

			sel.setString(1, url.toString());
			ResultSet rs = sel.executeQuery();
			rs.next();
			extractRule = rs.getString(1);
			rs.close();

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (conn != null) {
				DbUtil.stopServer(conn);
			}
		}
		if (extractRule == null) {
			extractRule = "";
		}
		return extractRule;
	}

}
