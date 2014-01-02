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
package net.sasasin.sreader.batch;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.arnx.jsonic.JSON;
import net.sasasin.sreader.commons.dao.EftRulesDao;
import net.sasasin.sreader.commons.dao.impl.EftRulesDaoHibernateImpl;
import net.sasasin.sreader.commons.entity.EftRules;
import net.sasasin.sreader.commons.util.Md5Util;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EftRulesDriver {
	private static Logger logger = LoggerFactory.getLogger("net.sasasin.sreader.batch");
	
	private URL url = null;
	private String jsonString = null;
	private Map<String, String> jsonMap = null;
	private EftRulesDao eftRulesDao = new EftRulesDaoHibernateImpl();

	public static void main(String[] args) {
		new EftRulesDriver().run();
	}

	public EftRulesDriver() {
	}

	public void run() {
		logger.info(this.getClass().getSimpleName() +" is started.");
		
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

		logger.info(this.getClass().getSimpleName() +" is ended.");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Map<String, String> parseJson() {
		Map<String, String> ldrFullFeed = new HashMap<String, String>();
		// URLとXPathだけ抽出。
		for (Map m : (List<Map>) JSON.decode(jsonString)) {
			Map<String, String> data = (Map<String, String>) m.get("data");
			String url = data.get("url");
			String xpath = data.get("xpath");
			ldrFullFeed.put(url, xpath);
		}
		return ldrFullFeed;
	}

	public void importEftRules(Map<String, String> json) {
		for (String key : json.keySet()) {
			// とりあえず探してみる
			EftRules er = eftRulesDao.get(Md5Util.crypt(key));
			// いなければnull
			if (er == null) {
				er = new EftRules();
				er.setUrl(key);
				er.setId(Md5Util.crypt(er.getUrl()));
				er.setExtractRule(json.get(key));
				eftRulesDao.save(er);
			} else {
				er.setExtractRule(json.get(key));
				eftRulesDao.update(er);
			}
		}
	}
}
