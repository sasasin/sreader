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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import net.sasasin.sreader.orm.LoginRules;

public class Wget {

	public static void main(String[] args) {
		if (args.length < 1) {
			return;
		}
		try {
			Wget w = new Wget(new URL(args[0]));
			String c = w.readWithoutLogin();
			System.out.println(args[0]);
			System.out.println(c);

		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	private URL url;

	public Wget(URL url) {
		setUrl(url);
	}

	public URL getUrl() {
		return url;
	}

	public String readWithoutLogin() {
		HttpClient httpclient = new DefaultHttpClient();
		try {
			HttpResponse response;
			response = httpclient.execute(new HttpGet(getUrl().toString()));
			String r = read(response.getEntity().getContent());
			r = r.replaceAll("charset=(.*?)\"", "charset=UTF-8\"");
			return r;
		} catch (IOException e) {
			return "";
		} finally {
			httpclient.getConnectionManager().shutdown();
		}
	}

	public String read(InputStream is) {
		String result = null;
		try {
			byte[] buf = IOUtils.toByteArray(is);
			String enc = CharDetector.detect(buf);
			if (enc != null) {
				result = new String(buf, enc);
			} else {
				result = new String(buf);
			}
		} catch (IOException e) {
			e.printStackTrace();
			result = "";
		}
		return result;
	}

	public String readWithLogin(LoginRules loginInfo, String loginId,
			String loginPassword) {
		String r = null;
		HttpClient httpclient = new DefaultHttpClient();
		try {
			// access top page.
			HttpResponse response = httpclient.execute(new HttpGet("http://"
					+ loginInfo.getHostName()));
			response.getEntity().consumeContent();

			// login
			HttpPost httpost = new HttpPost(loginInfo.getPostUrl());
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair(loginInfo.getIdBoxName(), loginId));
			nvps.add(new BasicNameValuePair(loginInfo.getPasswordBoxName(),
					loginPassword));
			httpost.setEntity((HttpEntity) new UrlEncodedFormEntity(nvps,
					HTTP.UTF_8));
			response = httpclient.execute(httpost);
			response.getEntity().consumeContent();

			// get contents.
			response = httpclient.execute(new HttpGet(getUrl().toString()));
			r = read(response.getEntity().getContent());
			r = r.replaceAll("charset=(.*?)\"", "charset=UTF-8\"");

		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			httpclient.getConnectionManager().shutdown();
		}
		return r;
	}

	public void setUrl(URL url) {
		this.url = url;
	}
}
