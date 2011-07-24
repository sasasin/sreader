#!/bin/sh

BASEDIR=$(cd $(dirname $0);pwd)

./h2ctl.sh restart

cd $BASEDIR/../

# FeedReaderの実行
# RSS/AtomのURLリストは$HOME/sreader.txt
java -cp $BASEDIR/../lib/\* \
    net.sasasin.sreader.FeedReader

# HTMLで配信
# $HOME/sreader.htmlが作成される
java -cp $BASEDIR/../lib/\* \
    net.sasasin.sreader.publish.HtmlPublisher

# GMailで配信
# gmail_login_infoテーブルに、データを入れておかないと、空回りして悲しい
java -cp $BASEDIR/../lib/\* \
    net.sasasin.sreader.publish.GMailPublisher

cd $BASEDIR
./h2ctl.sh stop
