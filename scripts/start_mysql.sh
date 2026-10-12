#!/usr/bin/env bash
# Script to verify or start the MySQL 8 Docker container for DevOpsHub
set -e

CONTAINER_NAME="devopshub-mysql"

if docker ps --filter "name=$CONTAINER_NAME" --filter "status=running" | grep -q "$CONTAINER_NAME"; then
    echo "MySQL container '$CONTAINER_NAME' is already running on port 3306."
elif docker ps -a --filter "name=$CONTAINER_NAME" | grep -q "$CONTAINER_NAME"; then
    echo "Starting existing stopped container '$CONTAINER_NAME'..."
    docker start "$CONTAINER_NAME"
else
    echo "Creating and starting new container '$CONTAINER_NAME' on port 3306..."
    docker run -d --name "$CONTAINER_NAME" -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=devopshub --restart unless-stopped mysql:8.0
fi

echo "Waiting for MySQL to accept connections..."
until docker exec "$CONTAINER_NAME" mysqladmin ping -u root -proot --silent >/dev/null 2>&1; do
    sleep 1
done

echo "MySQL is healthy and ready for DevOpsHub connections!"
