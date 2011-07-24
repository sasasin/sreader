#!/bin/sh

BASEDIR=$(cd $(dirname $0);pwd)
SQLFILE="$2"

start_h2(){
    if [ ! -e ~/h2datafiles ]; then
	mkdir ~/h2datafiles
    fi
    java -cp $BASEDIR/../lib/\* \
	org.h2.tools.Server \
	-baseDir ~/h2datafiles \
	-web -tcp -pg &
}

stop_h2(){
    java -cp $BASEDIR/../lib/\* \
	org.h2.tools.Server \
	-tcpShutdown tcp://localhost:9092
}

runscript(){
    java -cp $BASEDIR/../lib/\* \
	org.h2.tools.RunScript \
	-continueOnError \
	-url 'jdbc:h2:tcp://localhost/~/h2datafiles/sreader' \
	-user 'sa' -password '' \
	-script "$SQLFILE"
}

shell(){
    java -cp $BASEDIR/../lib/\* \
	org.h2.tools.Shell \
	-url 'jdbc:h2:tcp://localhost/~/h2datafiles/sreader' \
	-user 'sa' -password ''
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
