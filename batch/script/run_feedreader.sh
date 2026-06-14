#!/bin/sh
#
# SReader is RSS/Atom feed reader with full text.
#
# LEGACY-ONLY: the standard runtime is now the Spring Boot app scheduler.
# Do not use this script for the Docker Compose app workflow.
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

# RSS/AtomのURLリストは$HOME/sreader.txt
docker compose run --rm -v "$HOME:/host-home:ro" -e JAVA_TOOL_OPTIONS="-Duser.home=/host-home" \
    maven java -cp "/workspace/batch/lib/*:/workspace/batch/lib_ext/*" \
    net.sasasin.sreader.batch.SingleAccountFeedReader

docker compose run --rm \
    maven java -cp "/workspace/batch/lib/*:/workspace/batch/lib_ext/*" \
    net.sasasin.sreader.batch.ContentHeaderDriver

docker compose run --rm \
    maven java -cp "/workspace/batch/lib/*:/workspace/batch/lib_ext/*" \
    net.sasasin.sreader.batch.ContentFullTextDriver

cd "$BASEDIR"
