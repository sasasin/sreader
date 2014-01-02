/*
 * SReader is RSS/Atom feed reader with full text.
 *
 * Copyright (C) 2011, Shinnosuke Suzuki <sasasin@sasasin.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *	
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package net.sasasin.sreader.commons.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import net.sasasin.sreader.commons.exception.UnSupportedAlgorithmError;

/**
 * @author sasasin
 * 
 */
public class Md5Util {
	public static String crypt(String orig) {

		if (orig == null || orig.length() == 0) {
			throw new IllegalArgumentException();
		}

		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new UnSupportedAlgorithmError(e);
		}
		StringBuffer md5 = null;
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

	}
}
