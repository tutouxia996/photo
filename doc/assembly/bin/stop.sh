#!/bin/bash
pid=`ps ax | grep -i 'sq-admin-server' | grep java | grep -v grep | awk '{print $1}'`
if [ -z "$pid" ] ; then
        echo "No sq-admin-server Server running."
        exit -1;
fi

kill -9 ${pid}

echo "Send shutdown request to sq-admin-servere(${pid}) OK"


