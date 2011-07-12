/**
 * 
 */
package net.sasasin.sreader.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author sasasin
 * 
 */
public class Md5Util {
	public static String crypt(String orig) {

		try {
			StringBuffer md5 = null;
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(orig.getBytes());
			byte[] hash = md.digest();
			md5 = new StringBuffer(hash.length * 2);
			for (int i = 0; i < hash.length; i++) {
				int d = hash[i];
				if (d < 0) {
					d += 256;
				}
				if (d < 16) {
					md5.append("0");
				}
				md5.append(Integer.toString(d, 16));
			}
			return md5.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}

	}
}
