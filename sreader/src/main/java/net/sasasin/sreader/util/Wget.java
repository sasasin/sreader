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
import org.apache.http.util.EntityUtils;

import net.sasasin.sreader.ormap.LoginRule;

public class Wget {

	URL url;

	public Wget(URL url) {
		this.url = url;
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
			r = read(response.getEntity().getContent());

		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			httpclient.getConnectionManager().shutdown();
		}
		return r;
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
	
	private String read(InputStream is) {
		try {
			BufferedReader r;
			r = new BufferedReader(new InputStreamReader(is));
			StringBuilder s = new StringBuilder();
			String tmp = null;
			while ((tmp = r.readLine()) != null) {
				s.append(tmp);
				s.append('\n');
			}
			return s.toString();
		} catch (FileNotFoundException e) {
			System.out.println("FAIL; " + url.toString());
			return "";
		} catch (IOException e) {
			System.out.println("FAIL; " + url.toString());
			return "";
		}
	}

	public String read() {
		try {
			return read(url.openStream());
		} catch (IOException e) {
			return "";
		}

	}

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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
