#!/bin/bash

# Log where we are for your peace of mind
echo "Current directory: $(pwd)"
echo "Checking for plugins in /transfer..."

# Create the folder in the mounted volume
mkdir -p /data/plugins

# Copy from the MOUNTED volume (see docker-compose below)
if [ -d "/transfer" ]; then
    cp -f /transfer/*.jar /data/plugins/ 2>/dev/null
    echo "Plugins synced."
fi

# Fix permissions for the /data folder only (Don't do chmod 777 /)
chmod -R 777 /data

# Start the server
exec java -Xms2G -Xmx4G -jar /arclight.jar nogui