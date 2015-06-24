#!/bin/zsh -v
echo ""
echo "  Usage:  sudo -u datomic ./transactor-start.zsh "
echo ""

export XMX=-Xmx4g
export XMS=-Xms4g
cd /opt/datomic
./bin/transactor ./dev-transactor-template.properties

