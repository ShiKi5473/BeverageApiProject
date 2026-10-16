# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
# 改用專案內的 Maven Wrapper (./mvnw) 建置，與本機 / CI 使用相同的 Maven 版本（見 .mvn/wrapper）
COPY .mvn .mvn
COPY mvnw .
# 確保 mvnw 有執行權限，並移除 Windows 換行字元（避免 "bad interpreter: ^M"）
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY pom.xml .
# 先只下載依賴，利用 Docker layer cache（pom.xml 沒變時不必重新下載）
RUN ./mvnw -B -q dependency:go-offline
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 設定時區（營業日界線與日結報表皆以台北時間計算），alpine 需額外安裝 tzdata
RUN apk add --no-cache tzdata
ENV TZ=Asia/Taipei
# 以非 root 使用者執行，降低容器被入侵時的影響範圍
RUN addgroup -S app && adduser -S app -G app
# 將編譯好的 jar 複製過來並重新命名為 app.jar
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app
EXPOSE 8080
# MaxRAMPercentage：讓 JVM 堆積依容器記憶體上限自動調整（保留 25% 給 metaspace / 堆外記憶體）
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
