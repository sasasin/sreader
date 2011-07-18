package net.sasasin.sreader.http;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
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

			String tmp = null;
			while ((tmp = r.readLine()) != null) {
				s.append(tmp + '\n');
			}
			return s.toString();
		} catch (FileNotFoundException e) {
			// e.printStackTrace();
			System.out.println("FAIL; " + url.toString());
			return "";
		} catch (IOException e) {
			// e.printStackTrace();
			System.out.println("FAIL; " + url.toString());
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
