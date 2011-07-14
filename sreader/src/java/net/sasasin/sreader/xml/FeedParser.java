/**
 * 
 */
package net.sasasin.sreader.xml;

import java.util.Map;

/**
 * @author sasasin
 *
 */
public interface FeedParser {
	
	public void setFeed(String feed);
		
	public Map<String, String> parseFeed();
}
