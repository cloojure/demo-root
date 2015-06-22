#!/bin/zsh -v

# manual load results in this:
#   
#   /opt/datomic > sudo -u datomic bin/datomic restore-db file:///opt/datomic/mbrainz-1968-1973 datomic:dev://localhost:4334/mbrainz-1968-1973 
#   Copied 0 segments, skipped 0 segments.
#   Copied 824 segments, skipped 0 segments.
#   Copied 1048 segments, skipped 0 segments.
#   :succeeded
#   {:event :restore, :db mbrainz-1968-1973, :basis-t 130223, :inst #inst "2014-11-18T03:42:09.096-00:00"}
#   /opt/datomic > 
#   
#-----------------------------------------------------------------------------   
# This is untested still (AWT 2015/6/21)
cd /opt/datomic
sudo  --user=datomic  ./bin/datomic restore-db \
  file:///home/alan/demo/datomic/basic/mbrainz-1968-1973  \
  datomic:dev://localhost:4334/mbrainz-1968-1973

  # prints progress -- ~1,000 segments in restore

