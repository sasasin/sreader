#!/bin/sh

start_h2(){
    if [ ! -e ~/h2datafiles ]; then
	mkdir ~/h2datafiles
    fi
    java -cp $(dirname $0)/../../lib/h2-1.3.157.jar \
	org.h2.tools.Server \
	-baseDir ~/h2datafiles \
	-web -tcp -pg &
}

stop_h2(){
    java -cp $(dirname $0)/../../lib/h2-1.3.157.jar \
	org.h2.tools.Server \
	-tcpShutdown tcp://localhost:9092
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
esac
