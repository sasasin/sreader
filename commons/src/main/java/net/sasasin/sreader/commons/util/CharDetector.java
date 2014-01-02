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

import java.nio.charset.CharacterCodingException;

import org.mozilla.universalchardet.UniversalDetector;

public class CharDetector {

	public static String detect(byte[] buf) throws CharacterCodingException {
		UniversalDetector detector = new UniversalDetector(null);
		int len = buf.length;
		detector.handleData(buf, 0, len);
		detector.dataEnd();
		String encoding = detector.getDetectedCharset();
		detector.reset();
		// 判定に失敗していたら、nullが返ってくる
		if (encoding == null) {
			// nullではなく、例外として返す
			throw new CharacterCodingException();
		}
		return encoding;
	}

}
