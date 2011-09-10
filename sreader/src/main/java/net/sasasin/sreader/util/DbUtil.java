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

import java.sql.Clob;
import java.sql.SQLException;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

/**
 * @author sasasin
 * 
 */
public class DbUtil {

	private static SessionFactory sf;

	public static SessionFactory getSessionFactory() {
		if (sf == null){
			sf = new Configuration().configure()
					.buildSessionFactory();
		}
		return sf;
	}
	
	public static Clob stringToClob(String str){
		return getSessionFactory().openSession().getLobHelper().createClob(str);
	}
	
	public static String clobToString(Clob clob) {
		try {
			return clob.getSubString(1, (int) clob.length());
		} catch (SQLException e) {
			e.printStackTrace();
			return "";
		}
	}
}
