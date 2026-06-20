FROM maven:3.9.11-eclipse-temurin-25

WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY app/pom.xml /workspace/app/pom.xml
RUN mvn -pl app org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
    -Dexec.mainClass=com.microsoft.playwright.CLI \
    -Dexec.args="install --with-deps chromium"

WORKDIR /app
COPY app/target/sreader-app-0.0.1-SNAPSHOT.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
