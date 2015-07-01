#!/bin/zsh -v
echo ""
echo "  Usage:  ./console-start.zsh "
echo ""

export XMX=-Xmx4g
export XMS=-Xms1g
cd /opt/datomic
./bin/console -p 8080 dev datomic:dev://localhost:4334 
