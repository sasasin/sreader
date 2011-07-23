#!/bin/sh

cd $(dirname $0)/../

# アプリのコンパイル
mvn compile

# シェルスクリプト実行に必要なライブラリ収集
mvn dependency:copy-dependencies -DoutputDirectory=lib

cd $(dirname $0)/bin
# h2 databaseの起動
./h2ctl.sh start

# スキーマオブジェクトの作成
java -cp $(dirname $0)/../lib/* \
    org.h2.tools.RunScript \
    -continueOnError \
    -url 'jdbc:h2:tcp://localhost/~/h2datafiles/sreader' \
    -user 'sa' -password '' \
    -script $(dirname $0)/ddl.sql

# マスターデータの投入
java -cp $(dirname $0)/../lib/* \
    org.h2.tools.RunScript \
    -continueOnError \
    -url 'jdbc:h2:tcp://localhost/~/h2datafiles/sreader' \
    -user 'sa' -password '' \
    -script $(dirname $0)/dml.sql

# h2 databaseの停止
./h2ctl.sh stop
