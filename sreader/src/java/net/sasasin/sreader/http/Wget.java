package net.sasasin.sreader.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class Wget {

	URL url;
	HttpURLConnection conn;

	public Wget(URL url) {
		this.url = url;
		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setUseCaches(false);
			conn.setRequestMethod("GET");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public String getContentType() {
		return conn.getContentType();
	}

	public String getContentEncoding() {
		return conn.getContentEncoding();
	}

	public InputStream getInputStream() {
		try {
			return conn.getInputStream();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	public String read() {
		try {
			BufferedReader r;
			r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder s = new StringBuilder();

			while (r.ready()) {
				s.append(r.readLine() + '\n');
			}
			return s.toString();
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			return;
		}

		try {
			Wget w = new Wget(new URL(args[0]));
			String t = w.getContentType();
			String c = w.read();
			System.out
					.println(args[0] + " " + t + " " + w.getContentEncoding());
			System.out.println(c);

		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
