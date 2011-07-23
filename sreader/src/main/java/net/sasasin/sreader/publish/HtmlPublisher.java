package net.sasasin.sreader.publish;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.FileUtils;

public class HtmlPublisher extends AbstractPublisher {

	private StringBuilder s = new StringBuilder();

	public static void main(String[] args) {
		new HtmlPublisher().run();
	}

	public void init() {
		s.append("<html>");
		s.append("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" /></head>");
		s.append("<body>");
	}

	public void finalize() {
		s.append("</body><html>");
		try {
			FileUtils.writeStringToFile(
					new File(System.getProperty("user.home")
							+ File.separatorChar + "sreader.html"),
					s.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void publish(Map<String, String> content) {
		s.append("<hr>");
		// title with url
		s.append("<h1><a href='" + content.get("url") + "'>"
				+ content.get("title") + "</a></h1>");
		s.append("<p>");
		// content full text
		s.append(content.get("full_text").replaceAll("(?m)^", "<p>"));
	}

}
