package net.sasasin.sreader.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtractContent {

	static Map<String, String> CHARREF;
	static {
		CHARREF = new HashMap<String, String>();
		CHARREF.put("&nbsp;", " ");
		CHARREF.put("&lt;", "<");
		CHARREF.put("&gt;", ">");
		CHARREF.put("&amp;", "&");
		CHARREF.put("&laquo;", "\\xc2\\xab");
		CHARREF.put("&raquo;", "\\xc2\\xbb");
	}

	private int threshold = 100; // 本文と見なすスコアの閾値
	private int min_length = 20; // 評価を行うブロック長の最小値
	private double decay_factor = 0.73; // 減衰係数(小さいほど先頭に近いブロックのスコアが高くなる)
	private double continuous_factor = 1.62; // 連続ブロック係数(大きいほどブロックを連続と判定しにくくなる)
	private int punctuation_weight = 10; // 句読点に対するスコア
	private String punctuations = "([、。，．「」！？]|\\.[^A-Za-z0-9]|,[^0-9]|!|\\?)"; // 句読点
	private String waste_expressions = "(?i)(Copyright|All Rights Reserved)"; // フッターに含まれる特徴的なキーワードを指定
	private boolean debug = false; // true の場合、ブロック情報を標準出力に

	public Map<String, String> analyse(String html) {
		// title、bodyを詰める
		Map<String, String> result = new HashMap<String, String>();
		Matcher m;

		List<String> regexes = new ArrayList<String>();
		// jp.wsj.com
		regexes.add("(?ims)<div class=\"articlePage\">(.*?)</div><!--article_story_body-->");
		// www3.nhk.or.jp
		regexes.add("(?ims)<p id=\"news_textbody\">(.*?)<!-- _ria_tail_ -->");
		for (String regex : regexes) {
			m = Pattern.compile(regex, Pattern.DOTALL | Pattern.UNIX_LINES)
					.matcher(html);
			if (m.find()) {
				result.put("body", stripTags(m.group()));
				break;
			}
		}
		// 結局取れなければ剥いて全部入れる
		if(!result.containsKey("body")){
			result.put("body", stripTags(html));
		}
		
		// いまいち
		if (debug) {
			result.put("title", extractTitle(html));
			if (html.matches("(?i)<\\/frameset>|<meta\\s+http-equiv\\s*=\\s*[\"\']?refresh[\'\"]?[^>]*url")) {
				// 取れないくらいなら全文詰めなおす
				result.put("body", html);
				return result;
			}

			// header & title
			// title = if html =~ /<\/head\s*>/im
			// html = $' #'
			// extract_title($`)
			// else
			// extract_title(html);
			// end

			// Google AdSense Section Target
			html = html
					.replaceAll(
							"(?m)<!--\\s*google_ad_section_start\\(weight=ignore\\)\\s*-->.*?<!--\\s*google_ad_section_end.*?-->",
							"");
			if (html.matches("<!--\\s*google_ad_section_start[^>]*-->")) {
				m = Pattern
						.compile(
								"(?m)<!--\\s*google_ad_section_start[^>]*-->(.*?)<!--\\s*google_ad_section_end.*?-->")
						.matcher(html);
				int i = 0;
				StringBuilder htmlTmp = new StringBuilder();
				while (m.find()) {
					htmlTmp.append(m.group(i));
					i++;
				}
				html = htmlTmp.toString();
			}

			// eliminate useless text
			html = eliminateUselessTags(html);

			/*
			 * // h? block including title
			 * html.gsub!(/(<h\d\s*>\s*(.*?)\s*<\/h\d\s*>)/is) do |m| if
			 * $2.length >= 3 && title.include?($2) then "<div>#{$2}</div>" else
			 * $1 end end
			 */

			// extract text blocks
			double factor = 1.0;
			double continuous = 1.0;
			String body = "";
			double score = 0;
			String notlinked = "";
			List<String> bodylist = new ArrayList<String>();
			List<Double> scorelist = new ArrayList<Double>();

			List<String> list = Arrays
					.asList(html
							.split("(?im)<\\/?(?:div|center|td)[^>]*>|<p\\s*[^>]*class\\s*=\\s*[\"\']?(?:posted|plugin-\\w+)[\'\"]?[^>]*>"));
			for (String block : list) {
				if (block.isEmpty()) {
					continue;
				}
				block = block.trim();
				if (hasOnlyTags(block)) {
					continue;
				}
				if (!block.isEmpty()) {
					continuous = continuous / continuous_factor;
				}
				// リンク除外＆リンクリスト判定
				notlinked = eliminateLink(block);
				if (notlinked.length() < min_length) {
					continue;
				}

				// スコア算出
				double c = (notlinked.length() + Pattern.compile(punctuations)
						.matcher(notlinked).groupCount()
						* punctuation_weight)
						* factor;
				factor = factor * decay_factor;
				double not_body_rate = Pattern.compile(waste_expressions)
						.matcher(block).groupCount()
						+ Pattern
								.compile(
										"(?i)(amazon[a-z0-9\\.\\/\\-\\?&]+-22)")
								.matcher(block).groupCount() / 2.0;
				if (not_body_rate > 0) {
					c = c * (0.72 * not_body_rate * not_body_rate);
				}
				double c1 = c * continuous;

				// ブロック抽出＆スコア加算
				if (c1 > threshold) {
					body += block + "\n";
					score += c1;
					continuous = continuous_factor;
				} else if (c > threshold) {
					// continuous block end
					bodylist.add(stripTags(body));
					scorelist.add(score);
					body = block + "\n";
					score = c;
					continuous = continuous_factor;
				}
			}
			bodylist.add(stripTags(body));
			scorelist.add(score);

			double biggest = Collections.max(scorelist);
			int i = 0;
			for (double s : scorelist) {
				if (s == biggest) {
					result.put("body", bodylist.get(i));
					break;
				}
				i++;
			}
		}
		return result;
	}

	private String extractTitle(String st) {
		Matcher m = Pattern.compile("<title[^>]*>\\s*(.*?)\\s*<\\/title\\s*>")
				.matcher(st);
		if (m.lookingAt()) {
			return stripTags(m.group());
		}
		return "";
	}

	// Eliminates useless tags.
	private String eliminateUselessTags(String html) {
		// html = html
		// .replaceAll(
		// "\0342(?:\0200[\0230-\0235]|\0206[\0220-\0223]|\0226[\0240-\0275]|\0227[\0206-\0257]|\0230[\0205\0206])",
		// "");
		html = html
				.replaceAll(
						"(?ims)<(script|style|select|noscript)[^>]*>.*?<\\/\\1\\s*>",
						"");
		html = html
				.replaceAll(
						"(?ims)<(script|style|select|noscript)>.*?<\\/\\1\\s*>",
						"");
		html = html.replaceAll("(?m)<!--.*?-->", "");
		html = html.replaceAll("<![A-Za-z].*?>", "");
		html = html
				.replaceAll(
						"(?ims)<div\\s[^>]*class\\s*=\\s*[\'\"]?alpslab-slide[\"\']?[^>]*>.*?<\\/div\\s*>",
						"");
		html = html
				.replaceAll(
						"(?i)<div\\s[^>]*(id|class)\\s*=\\s*[\'\"]?\\S*more\\S*[\"\']?[^>]*>",
						"");
		return html;
	}

	// Checks if the given block has only tags without text.
	private boolean hasOnlyTags(String st) {
		if (st.replaceAll("(?im)<[^>]*>", "").replaceAll("&nbsp", "").trim()
				.length() == 0) {
			return true;
		}
		return false;
	}

	// リンク除外＆リンクリスト判定。
	private String eliminateLink(String html) {
		Matcher m = Pattern.compile("(?im)(<a\\s[^>]*>.*?<\\/a\\s*>)").matcher(
				html);
		int count = m.groupCount();
		String notlinked = stripTags(m.replaceAll("").replaceAll(
				"(?im)<form\\s[^>]*>.*?<\\/form\\s*>", ""));

		if (notlinked.length() < (20 * count) || isLinkList(html)) {
			return "";
		}
		return html;
	}

	// リンクリスト判定。
	// リストであれば非本文として除外する
	private boolean isLinkList(String st) {
		Matcher m = Pattern.compile("(?im)<(?:ul|dl|ol)(.+?)<\\/(?:ul|dl|ol)>")
				.matcher(st);
		if (m.lookingAt()) {
			String listpart = m.group();
			String outside = st
					.replaceAll("(?im)<(?:ul|dl)(.+?)<\\/(?:ul|dl)>", "")
					.replaceAll("<.+?>", "").replaceAll("\\s+", " ");
			List<String> list = Arrays.asList(listpart.split("<li[^>]*>"));
			if (!list.isEmpty()) {
				list.remove(0);
			}
			double rate = evaluateList(list);
			if (outside.length() <= st.length() / (45 / rate)) {
				return true;
			}
		}
		return false;
	}

	// リンクリストらしさを評価
	private double evaluateList(List<String> list) {
		if (list.isEmpty()) {
			return 1.0;
		}
		int hit = 1;
		for (String s : list) {
			// aタグ スペース文字 href= シングルかダブルのクォートが一つ
			// そのあとシングルかダブルクォートあるいはスペース以外の文字が任意個続き、
			// href=に続いてたシングルかダブルクォートで閉じてる文字列。
			if (Pattern.compile("(?im)<a\\s+href=([\'\"]?)([^\"\'\\s]+)\1")
					.matcher(s).lookingAt()) {
				hit++;
			}
		}
		// double ^ int はNGらしいのでこの書き方。
		return 9 * (1.0 * hit / list.size()) * (1.0 * hit / list.size()) + 1;
	}

	// Strips tags from html.
	public String stripTags(String html) {
		String st;
		st = html.replaceAll("<.+?>", "");

		//
		// Convert from wide character to ascii が入る予定。
		//

		for (String key : CHARREF.keySet()) {
			st = st.replaceAll(key, CHARREF.get(key));
		}

		//
		// unescapeHTML() が入る予定。
		//

		st = st.replaceAll("[ \t]+", " ");
		st = st.replaceAll("\n\\s*", "\n");
		return st;
	}
}
