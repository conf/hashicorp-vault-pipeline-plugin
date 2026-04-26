FROM maven:3.9-eclipse-temurin-21
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:resolve dependency:resolve-plugins -Dmaven.artifact.threads=20
COPY src ./src
CMD ["mvn", "-B", "test", "-Dmaven.artifact.threads=20"]
