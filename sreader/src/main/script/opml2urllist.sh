#!/bin/sh

cat "$1" \
| sed 's/ /\n/g' \
| grep -i xmlUrl \
| sed 's/"/\n/g' \
| grep -v xmlUrl \
| sort -u \
> url.txt
