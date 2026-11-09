# =============================================================
# YemomBizGrow Backend — multi-stage Docker build
# Stage 1: build the fat-jar with Maven
# Stage 2: lean JRE 21 runtime image
# =============================================================

# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Maven wrapper + pom first so the dependency layer is cached
COPY backend/.mvn           .mvn
COPY backend/mvnw           mvnw
COPY backend/pom.xml        pom.xml

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -q

# Copy source, then build (skip integration tests in Docker build; CI runs them)
COPY backend/src src

RUN ./mvnw -B clean package -DskipITs -DskipTests=false -q

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S bizgrow && adduser -S bizgrow -G bizgrow

WORKDIR /app

# Copy the fat-jar produced by spring-boot:repackage
COPY --from=builder /workspace/target/bizgrow-backend-*.jar app.jar

# Healthcheck using the Spring Boot actuator readiness probe
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=12 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

USER bizgrow

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
