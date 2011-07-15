package net.sasasin.sreader.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

public class Wget {

	URL url;

	public Wget(URL url) {
		this.url = url;
	}

	public String read() {
		try {
			BufferedReader r = new BufferedReader(new InputStreamReader(
					url.openStream()));
			StringBuffer s = new StringBuffer();

			while (r.ready()) {
				s.append(r.readLine());
			}
			return s.toString();
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}
	}

}
