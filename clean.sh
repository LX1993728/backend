#!/bin/sh
psql -U ticketuser ticketdb -c "\lo_list" | grep ^' [0-9]' | grep -o '[0-9]\+' > /tmp/out.txt
while read line
do
    echo $line
done < /tmp/out.txt

