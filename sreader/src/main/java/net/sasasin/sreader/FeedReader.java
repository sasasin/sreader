/**
 * 
 */
package net.sasasin.sreader;

import java.io.File;

/**
 * @author sasasin
 * 
 */
public class FeedReader {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			return;
		}

		File path = new File(System.getProperty("user.home")
				+ File.pathSeparator + "sreader.txt");
		if (!path.exists() || !path.isFile() || !path.canRead()) {
			System.out.println("FAIL;" + path.getPath() + " can not proc.");
			return;
		}

		new FeedReader().run(path);

		System.out.println("Finished!");
	}

	public void run(File path) {
		// import path to feed_url table.
		new FeedUrlDriver(path).run();

		// import RSS/Atom to content_header table.
		new ContentHeaderDriver().run();

		// get content full text.
		new ContentFullTextDriver().run();
	}

}
