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

		new FeedReader().run(args);
		
	}

	public void run(String[] args) {

		File path = new File(System.getProperty("user.home")
				+ File.separatorChar + "sreader.txt");

		if (!path.exists() || !path.isFile() || !path.canRead()) {
			System.out.println("FAIL;" + path.getPath() + " can not proc.");
			return;
		}

		// import Extract full text rules from 
		// http://wedata.net/databases/LDRFullFeed/items
		new EftRulesDriver().run();
		
		// import path to feed_url table.
		new FeedUrlDriver(path).run();

		// import RSS/Atom to content_header table.
		new ContentHeaderDriver().run();

		// get content full text.
		new ContentFullTextDriver().run();

	}

}
