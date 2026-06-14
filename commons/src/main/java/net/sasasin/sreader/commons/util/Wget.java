package net.sasasin.sreader.commons.util;

import java.net.URL;

public interface Wget {

	public void setUrl(URL url);

	public URL getUrl();

	public String read();

	public URL getOriginalUrl();

}
