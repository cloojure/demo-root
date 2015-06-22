#!/bin/zsh -v
export XMX=-Xmx4g
export XMS=-Xms4g
cd /opt/datomic
./bin/transactor ./dev-transactor-template.properties
