#!/usr/bin/python
import os
import time

username = 'ticketuser'
dbname = 'ticketdb'
backupdir = '/dbbackup'
date = time.strftime('%Y-%m-%d')

os.popen("pg_dump -h localhost -U %s %s | gzip > %s/%s.%s.gz" % (username, dbname, backupdir, dbname, date))

