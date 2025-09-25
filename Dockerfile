FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle

# Copy source code
COPY src ./src

# Build the application
RUN chmod +x gradlew && ./gradlew clean build -x test

# Run the application
EXPOSE 8080
CMD ["java", "-jar", "build/libs/api-server-0.0.1-SNAPSHOT.jar"]