FROM eclipse-temurin:17-jdk
WORKDIR /app
ARG VERSION=1.0.0
LABEL version=$VERSION
ARG JAR_FILE
COPY $JAR_FILE app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]