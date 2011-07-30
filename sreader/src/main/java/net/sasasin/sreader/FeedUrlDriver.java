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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.Transaction;

import net.sasasin.sreader.orm.FeedUrl;
import net.sasasin.sreader.util.DbUtil;
import net.sasasin.sreader.util.Md5Util;

/**
 * @author sasasin
 *
 */
public class FeedUrlDriver {

	private File path;

	public FeedUrlDriver(File path){
		setPath(path);
	}

	public File getPath() {
		return path;
	}
		
	public void importFeedUrl(List<FeedUrl> fl) {
		Session s = DbUtil.getSessionFactory().openSession();
		Transaction tx = s.beginTransaction();
		for (FeedUrl f : fl) {
			// 投入前に重複チェック
			FeedUrl f2 = (FeedUrl)s.get(FeedUrl.class, f.getId());
			if (f2 == null){
				// キーで探して居なければ投入
				s.save(f);
			}
		}
		tx.commit();
	}

	public void run(){
		List<FeedUrl> ful = this.textFileToList(path);
		this.importFeedUrl(ful);
	}
	private void setPath(File path) {
		this.path = path;
	}
	
	public List<FeedUrl> textFileToList(File path) {
		List<FeedUrl> fu = new ArrayList<FeedUrl>();
		try {
			BufferedReader r = new BufferedReader(new FileReader(path));
			while (r.ready()) {
				String[] s = r.readLine().split("\t");
				FeedUrl f = new FeedUrl();
				f.setId(Md5Util.crypt(s[0]));
				f.setUrl(s[0]);
				f.setAccountId("hoge");
				if (s.length == 3 ){
					f.setAuthName(s[1]);
					f.setAuthPassword(s[2]);
				}
				fu.add(f);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return fu;
	}

}
