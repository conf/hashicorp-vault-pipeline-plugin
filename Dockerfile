FROM maven:3.8.6-openjdk-11
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:resolve dependency:resolve-plugins -Dmaven.artifact.threads=20
COPY src ./src
CMD ["mvn", "-B", "test", "-Dmaven.artifact.threads=20"]
