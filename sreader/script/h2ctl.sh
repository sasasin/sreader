#!/bin/sh
#
# SReader is RSS/Atom feed reader with full text.
#
# Copyright (C) 2011, Shinnosuke Suzuki <sasasin@sasasin.net>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Lesser General Public License as
# published by the Free Software Foundation, either version 3 of
# the License, or any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this program.
# If not, see <http://www.gnu.org/licenses/>.
#

BASEDIR=$(cd $(dirname $0);pwd)
SQLFILE="$2"
JDBCURL='jdbc:h2:tcp://localhost/~/h2datafiles/sreader'

start_h2(){
    if [ ! -e ~/h2datafiles ]; then
	mkdir ~/h2datafiles
    fi
    java -cp $BASEDIR/../lib_ext/\* \
	org.h2.tools.Server \
	-baseDir ~/h2datafiles \
	-web -tcp -tcpAllowOthers -pg &
}

stop_h2(){
    java -cp $BASEDIR/../lib_ext/\* \
	org.h2.tools.Server \
	-tcpShutdown tcp://localhost:9092
}

runscript(){
    java -cp $BASEDIR/../lib_ext/\* \
	org.h2.tools.RunScript \
	-continueOnError \
	-url $JDBCURL -user 'sa' -password '' \
	-script "$SQLFILE"
}

shell(){
    java -cp $BASEDIR/../lib_ext/\* \
	org.h2.tools.Shell \
	-url $JDBCURL -user 'sa' -password ''
}

case $1 in
    start)
	start_h2
	;;
    stop)
	stop_h2
	;;
    restart)
	stop_h2
	start_h2
	;;
    runsql)
	runscript
	;;
	shell)
	shell
	;;
esac
