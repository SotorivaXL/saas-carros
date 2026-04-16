FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY apps/api/pom.xml ./

RUN mvn -q -DskipTests dependency:go-offline

COPY apps/api/src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre-jammy AS runner

WORKDIR /app

RUN useradd --system --uid 1001 --create-home spring

COPY --from=build /workspace/target/*.jar /app/app.jar

USER spring

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
