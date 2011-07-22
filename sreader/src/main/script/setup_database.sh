#!/bin/sh

./h2ctl.sh start

java -cp ../lib/h2-1.3.157.jar \
    org.h2.tools.RunScript \
    -continueOnError \
    -url 'jdbc:h2:tcp://localhost/~/h2datafiles/test' \
    -user 'sa' -password '' \
    -script ./ddl.sql

./h2ctl.sh stop
