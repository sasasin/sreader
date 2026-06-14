/*
 * SReader is RSS/Atom feed reader with full text.
 *
 * Copyright (C) 2013, Shinnosuke Suzuki <sasasin@sasasin.net>
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
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.CharacterCodingException;

import net.sasasin.sreader.commons.util.CharDetector;
import net.sasasin.sreader.commons.util.Wget;

import org.apache.commons.io.IOUtils;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;

/**
 * @author sasasin
 * 
 */
public class WgetHtmlUnitImpl implements Wget {

	private URL url;

	public WgetHtmlUnitImpl() {
		setUrl(null);
	}

	public WgetHtmlUnitImpl(URL url) {
		setUrl(url);
	}

	@Override
	public void setUrl(URL url) {
		this.url = url;
	}

	@Override
	public URL getUrl() {
		return url;
	}

	@Override
	public String read() {

		WebClient c = createWebClient();

		try {
			WebRequest req = new WebRequest(getUrl());
			WebResponse res = c.loadWebResponse(req);
			String result = read(res.getContentAsStream());
			c.closeAllWindows();
			return result;
		} catch (FailingHttpStatusCodeException | IOException e) {
			e.printStackTrace();
			c.closeAllWindows();
			return "";
		}
	}

	private String read(InputStream is) {
		String result = null;
		byte[] buf = null;
		try {
			// バイト配列に読み込む。
			// UTF8ではないソースからStringに読み込むと、文字化けるためと、
			// 文字化けたStringでは、CharDetectorが文字コード判定に失敗するため。
			buf = IOUtils.toByteArray(is);
		} catch (IOException e) {
			e.printStackTrace();
			result = "";
		}
		try {
			// 文字コード名を取得。
			String enc = CharDetector.detect(buf);
			// 取得した文字コードでStringに変換。
			result = new String(buf, enc);
		} catch (CharacterCodingException | UnsupportedEncodingException e) {
			// 判定に失敗してたら仕方ないのでそのままStringに変換。
			result = new String(buf);
		}
		return result;
	}

	private WebClient createWebClient() {

		WebClient c = new WebClient(BrowserVersion.getDefault());

		WebClientOptions copt = c.getOptions();
		// JavaScriptは有効にするが、JavaScriptエラー時に、Javaの例外として拾わないようにする
		copt.setJavaScriptEnabled(true);
		copt.setThrowExceptionOnScriptError(false);
		// CSSは有効にするが、CSSの構文エラーなどでいちいちエラーを出さないようにする
		copt.setCssEnabled(true);
		c.setCssErrorHandler(new SilentCssErrorHandler());
		// その他、こまごました設定
		copt.setPrintContentOnFailingStatusCode(false);
		copt.setThrowExceptionOnFailingStatusCode(false);
		copt.setActiveXNative(false);
		copt.setAppletEnabled(false);
		copt.setDoNotTrackEnabled(true);
		copt.setPopupBlockerEnabled(true);
		// HTTP 30xでリダイレクト先に飛ぶ
		copt.setRedirectEnabled(true);
		// あやしいSSL証明書でも気にしない
		copt.setUseInsecureSSL(true);

		return c;
	}

	@Override
	public URL getOriginalUrl() {
		return getUrl();
	}

}
