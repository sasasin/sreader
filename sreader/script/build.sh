#!/bin/sh

BASEDIR=$(pwd)/$(dirname $0)

cd $BASEDIR/../

# シェルスクリプト実行に必要なライブラリ収集
mvn dependency:copy-dependencies -DoutputDirectory=lib

# アプリのコンパイル
mvn clean
mvn clean package
cp $BASEDIR/../target/*.jar $BASEDIR/../lib

cd $BASEDIR

# h2 databaseの起動
./h2ctl.sh start
# スキーマオブジェクトの作成
./h2ctl.sh runsql $BASEDIR/ddl.sql
# マスターデータの投入
./h2ctl.sh runsql $BASEDIR/dml.sql
# h2 databaseの停止
./h2ctl.sh stop
