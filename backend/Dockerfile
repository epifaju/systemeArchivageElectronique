# Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Runtime — OCR (Alpine: packages tesseract-ocr, ocrmypdf via pip sur image élargie)
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr tesseract-ocr-fra tesseract-ocr-por \
    ghostscript qpdf python3 python3-pip \
    curl \
    && pip3 install --no-cache-dir ocrmypdf \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r archivage && useradd -r -g archivage archivage
USER archivage
WORKDIR /app
COPY --from=builder /app/target/archivage-backend.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
