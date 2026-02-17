# Use Java 17 for Minecraft 1.20.1
FROM eclipse-temurin:21-jre
WORKDIR /data
# ADD https://files.hypertention.cn/v1/objects/b1c77bf67e9239f0279b66d24dd797d6abf220f5 arclight.jar
COPY arclight.jar /arclight.jar
COPY entry.sh /entry.sh

# 2. Make the script executable
RUN chmod +x /entry.sh

EXPOSE 25565
ENTRYPOINT ["/bin/bash", "/entry.sh"]