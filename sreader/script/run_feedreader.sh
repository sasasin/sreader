#!/bin/sh

BASEDIR=$(pwd)/$(dirname $0)

./h2ctl.sh restart

cd $BASEDIR/../

# FeedReaderの実行
# RSS/AtomのURLリストは$HOME/sreader.txt
java -cp $BASEDIR/../lib/\* \
    net.sasasin.sreader.FeedReader

# GMailで配信
# gmail_login_infoテーブルに、データを入れておかないと、空回りして悲しい
java -cp $BASEDIR/../lib/\* \
    net.sasasin.sreader.publish.GMailPublisher

# HTMLで配信
# $HOME/sreader.htmlが作成される
java -cp $BASEDIR/../lib/\* \
    net.sasasin.sreader.publish.HtmlPublisher

./h2ctl.sh stop
