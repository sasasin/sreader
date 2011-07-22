package net.sasasin.sreader.publish;

import java.util.Map;

public class HtmlPublisher extends AbstractPublisher {

	public static void main(String[] args) {
		new HtmlPublisher().run();
	}

	public void init() {
		System.out.println("<html>");
		System.out
				.println("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" /></head>");
		System.out.println("<body>");
	}

	public void finalize() {
		System.out.println("</body><html>");
	}

	public void publish(Map<String, String> content) {
		System.out.println("<hr>");
		// title with url
		System.out.println("<h1><a href='" + content.get("url") + "'>"
				+ content.get("title") + "</a></h1>");
		System.out.println("<p>");
		// content full text
		System.out.println(content.get("full_text").replaceAll("(?m)^", "<p>"));
	}

}
