# Multi-stage build for Smart Commands application
FROM openjdk:21-jdk-slim as builder

WORKDIR /app

# Copy Gradle wrapper and cache dependencies
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies
RUN ./gradlew dependencies || true

# Copy source code
COPY src src

# Build the application
RUN ./gradlew build -x test

# Runtime stage
FROM openjdk:21-jre-slim

# Install necessary packages for clipboard functionality
RUN apt-get update && apt-get install -y \
    xclip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Create a non-root user
RUN groupadd -r smartcommands && useradd -r -g smartcommands smartcommands
USER smartcommands

# Expose port (if running as web service)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD java -jar app.jar --status || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]