# ===== Build stage =====
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# 의존성 캐싱: Gradle 설정 먼저 복사 → dependencies resolve
COPY gradle gradle
COPY gradlew .
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# 소스 복사 후 빌드
COPY src src
COPY data data
RUN ./gradlew --no-daemon bootJar -x test

# ===== Runtime stage =====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

# Railway/Render 는 $PORT 를 동적으로 주입
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 5173

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
