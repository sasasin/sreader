/**
 * 
 */
package net.sasasin.sreader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.sasasin.sreader.ormap.FeedUrl;

/**
 * @author sasasin
 * 
 */
public class FeedReader {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		if (args.length < 1) {
			return;
		}

		File path = new File(args[0]);
		if (!path.exists()) {
			return;
		}
		FeedReader fr = new FeedReader();
		fr.importFeedList(path);
	}

	public void importFeedList(File path) {
		try {
			List<FeedUrl> fu = new ArrayList<FeedUrl>();
			BufferedReader r = new BufferedReader(new FileReader(path));
			while (r.ready()) {
				String[] s = r.readLine().split("\t");
				fu.add(new FeedUrl(s[0]));
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return;
	}

	public void publishFeed() {

	}

}
