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
