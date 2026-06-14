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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import net.sasasin.sreader.commons.dao.FeedUrlDao;
import net.sasasin.sreader.commons.dao.impl.FeedUrlDaoHibernateImpl;
import net.sasasin.sreader.commons.entity.FeedUrl;
import net.sasasin.sreader.commons.util.Md5Util;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleAccountFeedReader {
	private static Logger logger = LoggerFactory.getLogger("net.sasasin.sreader.batch");

	private FeedUrlDao feedUrlDao = new FeedUrlDaoHibernateImpl();

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new SingleAccountFeedReader().run();
	}

	public void run() {
		logger.info(this.getClass().getSimpleName() +" is started.");
		
		File path = new File(System.getProperty("user.home")
				+ File.separatorChar + "sreader.txt");

		if (!path.exists() || !path.isFile() || !path.canRead()) {
			System.out.println("FAIL;" + path.getPath() + " can not proc.");
			return;
		}

		List<String> lines = null;
		try {
			lines = IOUtils.readLines(new FileReader(path));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		importFeedUrls(lines);

		logger.info(this.getClass().getSimpleName() +" is ended.");
	}

	private void importFeedUrls(List<String> lines) {
		for (String line : lines) {
			if (line.trim().length() == 0) {
				continue;
			}
			String[] str = line.split("\t");

			FeedUrl f = feedUrlDao.get(Md5Util.crypt(str[0]));
			if (f == null) {
				f = new FeedUrl();
				f.setId(Md5Util.crypt(str[0]));
				f.setUrl(str[0]);
				feedUrlDao.save(f);
			}
		}
	}
}
