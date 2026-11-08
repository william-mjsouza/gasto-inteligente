# syntax=docker/dockerfile:1
#
# Build multistage de distribuição: compila o frontend, empacota-o no Spring Boot e gera uma
# imagem de execução com JRE. Uma instância desta imagem e um PostgreSQL bastam (seção 2).
# Os testes não rodam aqui (dependem de Docker via Testcontainers); o CI os executa antes.

FROM node:24.22.2-alpine3.24 AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21.0.13_9-jdk-alpine-3.24 AS backend
WORKDIR /build/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
COPY backend/src/main/ src/main/
COPY --from=frontend /build/frontend/dist/ src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw \
    && ./mvnw -B -q -DskipTests package \
    && cp target/gasto-inteligente-backend-*.jar /build/app.jar

FROM eclipse-temurin:21.0.13_9-jre-alpine-3.24 AS runtime
RUN addgroup -S app && adduser -S -G app -H -h /app app
WORKDIR /app
COPY --from=backend --chown=app:app /build/app.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
