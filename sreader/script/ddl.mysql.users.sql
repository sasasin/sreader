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

-- show warnings
\W

set character set utf8;

grant all privileges on *.* to  'sreader'@'%'
identified by 'sreader' with grant option;

create database if not exists sreader;

alter database sreader character set utf8;


grant all privileges on *.* to  'sreadertest'@'%'
identified by 'sreadertest' with grant option;

create database if not exists sreadertest;

alter database sreadertest character set utf8;


use sreader;

source ./ddl.mysql.tables.sql

use sreadertest;

source ./ddl.mysql.tables.sql

