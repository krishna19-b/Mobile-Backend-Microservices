FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY target/config-server-*.jar app.jar

EXPOSE 8887

ENTRYPOINT ["java", "-jar", "app.jar"]