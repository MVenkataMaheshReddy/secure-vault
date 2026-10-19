# Dockerfile for SecureVault Spring Boot Backend
FROM eclipse-temurin:17-jdk-focal AS builder

WORKDIR /build

# Copy maven wrapper / pom.xml
COPY pom.xml .
COPY src ./src

# Build production jar without tests (tests verified prior)
RUN apt-get update && apt-get install -y maven && mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-focal

WORKDIR /app

# Install Tesseract OCR and English traineddata for OCR pipeline
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-eng \
    libtesseract-dev && \
    rm -rf /var/lib/apt/lists/*

# Copy built artifact
COPY --from=builder /build/target/securevault-0.0.1-SNAPSHOT.jar app.jar

# Create storage directories
RUN mkdir -p storage/documents storage/qr storage/tessdata

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
