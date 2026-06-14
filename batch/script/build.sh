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
REPO_DIR=$(cd "$BASEDIR/../..";pwd)

cd "$REPO_DIR"

# シェルスクリプト実行に必要なライブラリ収集
docker compose run --rm maven sh -c \
    'mkdir -p batch/lib_ext && rm -f batch/lib_ext/* && mvn -pl batch -am clean package install dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/workspace/batch/lib_ext && cp commons/target/*.jar batch/target/*.jar batch/lib_ext'
