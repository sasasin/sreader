package net.sasasin.sreader.util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.mozilla.intl.chardet.nsDetector;
import org.mozilla.intl.chardet.nsICharsetDetectionObserver;

import net.sasasin.sreader.ormap.LoginRule;

public class Wget {

	URL url;

	public static void main(String[] args) {
		if (args.length < 1) {
			return;
		}
		try {
			Wget w = new Wget(new URL(args[0]));
			String c = w.read();
			System.out.println(args[0]);
			System.out.println(c);

		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	public Wget(URL url) {
		CharDetector.result = "";
		this.url = url;
	}

	public void closeAllStream(HttpEntity he) {
		if (he == null) {
			return;
		}
		if (he.isStreaming()) {
			InputStream s;
			try {
				s = he.getContent();
				if (s != null) {
					s.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public String read() {
		HttpClient httpclient = new DefaultHttpClient();
		try {
			HttpResponse response;
			response = httpclient.execute(new HttpGet(this.url.toString()));
			String r = read(response.getEntity().getContent(), "UTF-8");
			String h = CharDetector.detect(r);
			if (!h.equals("UTF-8")) {
				// リトライ
				response = httpclient.execute(new HttpGet(this.url.toString()));
				r = read(response.getEntity().getContent(), "JISAutoDetect");
				r = r.replaceAll("charset=(.*?)\"", "charset=UTF-8\"");
			}
			return r;
		} catch (IOException e) {
			return "";
		}
	}

	private String read(InputStream is, String charset) {
		String result = null;
		BufferedReader r = null;
		try {
			r = new BufferedReader(new InputStreamReader(is, charset));
			StringBuilder s = new StringBuilder();
			String tmp = null;
			while ((tmp = r.readLine()) != null) {
				s.append(tmp);
				s.append('\n');
			}
			r.close();
			result = s.toString();
		} catch (FileNotFoundException e) {
			System.out.println("FAIL; " + url.toString());
			result = "";
		} catch (IOException e) {
			System.out.println("FAIL; " + url.toString());
			result = "";
		} finally {
			if (r != null) {
				try {
					r.close();
				} catch (IOException e) {
				}
			}
		}
		return result;
	}

	public String read(LoginRule loginInfo, String loginId, String loginPassword) {
		String r = null;
		HttpClient httpclient = new DefaultHttpClient();

		try {
			// access top page.
			HttpResponse response = httpclient.execute(new HttpGet("http://"
					+ loginInfo.getHostName()));
			closeAllStream(response.getEntity());

			// login
			HttpPost httpost = new HttpPost(loginInfo.getPostUrl());
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair(loginInfo.getIdBoxName(), loginId));
			nvps.add(new BasicNameValuePair(loginInfo.getPasswordBoxName(),
					loginPassword));
			httpost.setEntity((HttpEntity) new UrlEncodedFormEntity(nvps,
					HTTP.UTF_8));
			response = httpclient.execute(httpost);
			closeAllStream(response.getEntity());

			// get contents.
			response = httpclient.execute(new HttpGet(this.url.toString()));
			r = read(response.getEntity().getContent(), "UTF-8");
			String h = CharDetector.detect(r);
			if (!h.equals("UTF-8")) {
				// リトライ
				response = httpclient.execute(new HttpGet(this.url.toString()));
				r = read(response.getEntity().getContent(), "JISAutoDetect");
				r = r.replaceAll("charset=(.*?)\"", "charset=UTF-8\"");
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			httpclient.getConnectionManager().shutdown();
		}
		return r;
	}
}
