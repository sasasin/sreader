FROM maven:3.9.16-eclipse-temurin-25

WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY app/pom.xml /workspace/app/pom.xml
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN mvn -pl app org.codehaus.mojo:exec-maven-plugin:3.6.3:java \
    -Dexec.mainClass=com.microsoft.playwright.CLI \
    -Dexec.args="install --with-deps chromium"
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

WORKDIR /app
COPY app/target/sreader-app-0.0.1-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /var/lib/sreader/content-text

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
