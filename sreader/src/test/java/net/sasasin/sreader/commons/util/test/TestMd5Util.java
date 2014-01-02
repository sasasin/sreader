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
package net.sasasin.sreader.commons.util.test;

import static org.junit.Assert.*;
import static org.hamcrest.core.Is.is;
import net.sasasin.sreader.commons.util.Md5Util;

import org.junit.Test;

public class TestMd5Util {

	/**
	 * 半角英数で起動した場合。
	 */
	@Test
	public void testCryptNormal() {
		String input = "http://example.com/index.html";
		String expect = "37ec62c647afde56313a6a8aa2302901";
		String actual = null;

		actual = Md5Util.crypt(input);

		assertThat(actual, is(expect));
	}

	/**
	 * ブランクで起動した場合。
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testCryptByEmptyString() {
		String input = "";

		Md5Util.crypt(input);
		
		fail("例外にならないのは失敗");
	}

	/**
	 * Nullで起動した場合。
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testCryptByNull() {
		String input = null;

		Md5Util.crypt(input);

		fail("例外にならないのは失敗");
	}
}
