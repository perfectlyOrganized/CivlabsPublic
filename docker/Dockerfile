FROM eclipse-temurin:17-jre
WORKDIR /data

COPY arclight.jar /arclight.jar
COPY entry.sh /entry.sh

RUN sed -i 's/\r$//' /entry.sh && chmod +x /entry.sh

EXPOSE 25565
ENTRYPOINT ["/bin/bash", "/entry.sh"]