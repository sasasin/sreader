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

import net.sasasin.sreader.commons.entity.LoginRules;
import net.sasasin.sreader.commons.util.CharDetector;
import net.sasasin.sreader.commons.util.Wget;

import org.apache.commons.io.IOUtils;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;

/**
 * @author sasasin
 * 
 */
public class WgetHtmlUnitImpl implements Wget {

	private URL url;
	private LoginRules loginInfo;
	private String loginId;
	private String loginPassword;

	public WgetHtmlUnitImpl() {
		setUrl(null);
		setLoginInfo(null);
		setLoginId(null);
		setLoginPassword(null);
	}

	public WgetHtmlUnitImpl(URL url) {
		setUrl(url);
		setLoginInfo(null);
		setLoginId(null);
		setLoginPassword(null);
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
	public LoginRules getLoginInfo() {
		return loginInfo;
	}

	@Override
	public void setLoginInfo(LoginRules loginInfo) {
		this.loginInfo = loginInfo;
	}

	@Override
	public String getLoginId() {
		return loginId;
	}

	@Override
	public void setLoginId(String loginId) {
		this.loginId = loginId;
	}

	@Override
	public String getLoginPassword() {
		return loginPassword;
	}

	@Override
	public void setLoginPassword(String loginPassword) {
		this.loginPassword = loginPassword;
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

		// ログイン情報があれば、ログイン済みにする。
		if (getLoginId() != null && getLoginPassword() != null
				&& getLoginInfo() != null) {
			login(c);
		}

		return c;
	}

	private void login(WebClient c) {

		WebClientOptions copt = c.getOptions();
		// JavaScriptは有効にするが、JavaScriptエラー時に、Javaの例外として拾わないようにする
		copt.setJavaScriptEnabled(true);
		copt.setThrowExceptionOnScriptError(false);

		HtmlPage loginPage = null;
		HtmlInput idBox = null;
		HtmlPasswordInput passwordBox = null;
		HtmlSubmitInput submitButton = null;
		HtmlPage loginedPage = null;

		try {
			// ログインページにアクセス
			loginPage = c.getPage(getLoginInfo().getPostUrl());
		} catch (FailingHttpStatusCodeException e) {
			// ログインページにアクセスできなければ、ログインを諦める。
			System.out
					.println("login failed by login page access failed. status code is "
							+ e.getStatusCode()
							+ " status message "
							+ e.getStatusMessage());
			return;
		} catch (IOException e) {
			// ログインページにアクセスできなければ、ログインを諦める。
			System.out.println("login failed by login page access failed.");
			return;
		}

		try {
			// ログインID入力欄を取得
			try {
				idBox = loginPage.getElementByName(getLoginInfo()
						.getIdBoxName());
			} catch (ElementNotFoundException e) {
				// ByNameで取れない場合、ByIdでリトライする
				try {
					idBox = loginPage.getElementById(getLoginInfo()
							.getIdBoxName(), false);
				} catch (ElementNotFoundException e2) {
					// ByIdで取得できない場合、XPathでリトライする
					idBox = loginPage.getFirstByXPath(getLoginInfo()
							.getIdBoxName());
				}
			}

			// ログインパスワード入力欄を取得
			try {
				passwordBox = loginPage.getElementByName(getLoginInfo()
						.getPasswordBoxName());
			} catch (ElementNotFoundException e) {
				// ByNameで取れない場合、ByIdでリトライする
				try {
					passwordBox = loginPage.getElementById(getLoginInfo()
							.getPasswordBoxName(), false);
				} catch (ElementNotFoundException e2) {
					// ByIdで取得できない場合、XPathでリトライする
					passwordBox = loginPage.getFirstByXPath(getLoginInfo()
							.getPasswordBoxName());
				}
			}

			// ログインボタンを取得
			try {
				submitButton = loginPage.getElementByName(getLoginInfo()
						.getSubmitButtonName());
			} catch (ElementNotFoundException e) {
				// ByNameで取れない場合、ByIdでリトライする
				try {
					submitButton = loginPage.getElementById(getLoginInfo()
							.getSubmitButtonName(), false);
				} catch (ElementNotFoundException e2) {
					// ByIdで取得できない場合、XPathでリトライする
					submitButton = loginPage.getFirstByXPath(getLoginInfo()
							.getSubmitButtonName());
				}
			}

		} catch (ElementNotFoundException e) {
			// 何度やってもID入力欄/パス入力欄/ボタンが取得できなければ、ログインを諦める
			System.out.println("login failed by html elements cannot get.");
			return;
		}

		try {
			// ログインIDを入力
			idBox.setValueAttribute(loginId);
			// ログインパスワードを入力
			passwordBox.setValueAttribute(loginPassword);
			// ログインボタンをクリック
			loginedPage = submitButton.click();
			c = loginedPage.getWebClient();
			return;
		} catch (FailingHttpStatusCodeException | IOException e) {
			// ログイン後のページにアクセスできなければ、ログインを諦める。
			System.out.println("login failed by logined page cannot access.");
			return;
		}
	}

	@Override
	public URL getOriginalUrl() {
		return getUrl();
	}

}
