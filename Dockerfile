FROM eclipse-temurin:21-jre-noble

LABEL org.opencontainers.image.source="https://github.com/italo-jsm/ai"

WORKDIR /app
COPY build/libs/ai.jar /app/ai.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/ai.jar"]
