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
package net.sasasin.sreader.util;

import org.mozilla.intl.chardet.nsDetector;
import org.mozilla.intl.chardet.nsICharsetDetectionObserver;

public class CharDetector {
	public static String result = null;

	public static String detect(String s) {
		nsDetector det = new nsDetector(nsDetector.JAPANESE);
		// DoIt後、Reportが判定結果を引数にNotifyを呼んでくれる。
		det.Init(new nsICharsetDetectionObserver() {
			public void Notify(String charset) {
				result = charset;
			}
		});

		if (det.isAscii(s.getBytes(), s.getBytes().length)) {
			return "UTF-8";
		} else {
			det.DoIt(s.getBytes(), s.getBytes().length, false);
			det.Done();
			return result;
		}

	}
}
