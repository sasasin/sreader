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
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import net.sasasin.sreader.commons.entity.LoginRules;
import net.sasasin.sreader.commons.util.CharDetector;
import net.sasasin.sreader.commons.util.Wget;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

public class WgetHttpComponentsImpl implements Wget {

	private URL url;
	private LoginRules loginInfo;
	private String loginId;
	private String loginPassword;

	private final static int DEFAULT_TIMEOUT_MILLISECONDS = 3000;

	public WgetHttpComponentsImpl() {
		setUrl(null);
		setLoginInfo(null);
		setLoginId(null);
		setLoginPassword(null);
	}

	public WgetHttpComponentsImpl(URL url) {
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

		HttpResponse responce = null;
		try (CloseableHttpClient httpclient = httpClientFactory()) {
			responce = httpclient.execute(new HttpGet(getUrl().toString()));
			return read(responce.getEntity().getContent());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
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

	private CloseableHttpClient httpClientFactory() throws IOException {

		// HTTP 30xを追跡する
		RequestConfig config = RequestConfig.custom().setRedirectsEnabled(true)
				.setConnectTimeout(DEFAULT_TIMEOUT_MILLISECONDS)
				.setConnectionRequestTimeout(DEFAULT_TIMEOUT_MILLISECONDS)
				.build();

		// UserAgentを設定。無難にMSIE。
		List<Header> headers = new ArrayList<Header>();
		headers.add(new BasicHeader("User-Agent",
				"Mozilla/4.0 (compatible; MSIE 7.0; Windows NT 5.1)"));

		CloseableHttpClient httpclient = HttpClientBuilder.create()
				.setDefaultRequestConfig(config).setDefaultHeaders(headers)
				.build();

		HttpResponse response = null;

		// ログイン情報があれば、ログイン済みのHttpClientを返す。
		if (getLoginId() != null && getLoginPassword() != null
				&& getLoginInfo() != null) {

			// access top page.
			response = httpclient.execute(new HttpGet("http://"
					+ getLoginInfo().getHostName()));
			EntityUtils.consume(response.getEntity());

			// login
			HttpPost httpost = new HttpPost(getLoginInfo().getPostUrl());
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			// ログインID入力欄の名前と、ログインIDをセット
			nvps.add(new BasicNameValuePair(getLoginInfo().getIdBoxName(),
					getLoginId()));
			// パスワード入力欄の名前と、パスワードをセット
			nvps.add(new BasicNameValuePair(
					getLoginInfo().getPasswordBoxName(), getLoginPassword()));
			httpost.setEntity((HttpEntity) new UrlEncodedFormEntity(nvps,
					Charset.forName("UTF-8")));
			response = httpclient.execute(httpost);
			EntityUtils.consume(response.getEntity());
		}
		return httpclient;
	}

	@Override
	public URL getOriginalUrl() {

		// HTTP 30xを追跡しない
		RequestConfig config = RequestConfig.custom()
				.setRedirectsEnabled(false).setConnectTimeout(DEFAULT_TIMEOUT_MILLISECONDS)
				.setConnectionRequestTimeout(DEFAULT_TIMEOUT_MILLISECONDS).build();

		// UserAgentを設定。無難にMSIE。
		List<Header> headers = new ArrayList<Header>();
		headers.add(new BasicHeader("User-Agent",
				"Mozilla/4.0 (compatible; MSIE 7.0; Windows NT 5.1)"));

		HttpResponse responce = null;
		try (CloseableHttpClient httpclient = HttpClientBuilder.create()
				.setDefaultRequestConfig(config).setDefaultHeaders(headers)
				.build()) {
			responce = httpclient.execute(new HttpHead(getUrl().toString()));

			int httpStatusCode = responce.getStatusLine().getStatusCode();

			if (httpStatusCode == HttpStatus.SC_MOVED_PERMANENTLY
					|| httpStatusCode == HttpStatus.SC_MOVED_TEMPORARILY) {
				// 301,302の時は、Locationに本命のURLがある
				Header h = responce.getFirstHeader("Location");
				if (!getUrl().toString().equals(h.getValue())) {
					// URLが取れたら本命のURLを返す
					return new URL(h.getValue());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// 30xでなければインスタンス作成時に渡されたURLを返す。
		return getUrl();
	}

}
