#!/bin/bash

echo "Waiting for mysql-master to be ready..."
until docker exec mysql-master mysqladmin ping -u root -proot --silent; do
  sleep 2
done

echo "Waiting for mysql-slave to be ready..."
until docker exec mysql-slave mysqladmin ping -u root -proot --silent; do
  sleep 2
done

echo "Creating replication user on master..."
docker exec mysql-master mysql -u root -proot -e "
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY 'repl_password';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
"

echo "Getting master status..."
MASTER_STATUS=$(docker exec mysql-master mysql -u root -proot -e "SHOW MASTER STATUS\G")
FILE=$(echo "$MASTER_STATUS" | grep File | awk '{print $2}')
POSITION=$(echo "$MASTER_STATUS" | grep Position | awk '{print $2}')

echo "Master File: $FILE, Position: $POSITION"

echo "Configuring slave..."
docker exec mysql-slave mysql -u root -proot -e "
STOP REPLICA;
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='mysql-master',
  SOURCE_USER='repl',
  SOURCE_PASSWORD='repl_password',
  SOURCE_LOG_FILE='$FILE',
  SOURCE_LOG_POS=$POSITION;
START REPLICA;
"

echo "Replication setup complete! Checking slave status:"
docker exec mysql-slave mysql -u root -proot -e "SHOW REPLICA STATUS\G" | grep -E "Slave_IO_Running|Slave_SQL_Running|Seconds_Behind_Master"
