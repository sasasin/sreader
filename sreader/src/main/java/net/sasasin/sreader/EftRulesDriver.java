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
package net.sasasin.sreader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.hibernate.Session;
import org.hibernate.Transaction;

import net.arnx.jsonic.JSON;
import net.sasasin.sreader.orm.EftRules;
import net.sasasin.sreader.util.DbUtil;
import net.sasasin.sreader.util.Md5Util;

public class EftRulesDriver {
	private URL url = null;
	private String jsonString = null;
	private Map<String, String> jsonMap = null;

	public static void main(String[] args) {
		new EftRulesDriver().run();
	}

	public EftRulesDriver() {
	}

	public void run() {
		try {
			url = new URL("http://wedata.net/databases/LDRFullFeed/items.json");
			try {
				jsonString = IOUtils.toString(url.openStream());
				jsonMap = parseJson();
				importEftRules(jsonMap);
			} catch (IOException e) {
				e.printStackTrace();
				jsonString = "";
			}
		} catch (MalformedURLException e) {
		}

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Map<String, String> parseJson() {
		Map<String, String> ldrFullFeed = new HashMap<String, String>();
		// URLとXPathだけ抽出。
		for (Map m : (List<Map>) JSON.decode(jsonString)) {
			Map<String, String> data = (Map<String, String>) m.get("data");
			ldrFullFeed.put(data.get("url"), data.get("xpath"));
		}
		return ldrFullFeed;
	}

	public void importEftRules(Map<String, String> json) {
		Session session = DbUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		EftRules er = null;
		for (String key : json.keySet()) {
			// とりあえず探してみる
			er = (EftRules) session.get(EftRules.class, Md5Util.crypt(key));
			// いなければnull
			if (er == null) {
				er = new EftRules();
				er.setId(Md5Util.crypt(er.getUrl()));
			}
			er.setUrl(key);
			er.setExtractRule(json.get(key));
			session.save(er);
		}
		tx.commit();
	}
}
