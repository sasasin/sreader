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

cd $BASEDIR/../

# RSS/AtomのURLリストは$HOME/sreader.txt
java -cp $BASEDIR/../lib/\*:$BASEDIR/../lib_ext/\* \
    net.sasasin.sreader.SingleAccountFeedReader

# FeedReaderの実行
java -cp $BASEDIR/../lib/\*:$BASEDIR/../lib_ext/\* \
    net.sasasin.sreader.FeedReader

# GMailで配信
# gmail_login_infoテーブルに、データを入れておかないと、空回りして悲しい
java -cp $BASEDIR/../lib/\*:$BASEDIR/../lib_ext/\* \
    net.sasasin.sreader.publish.GMailPublisher

cd $BASEDIR
