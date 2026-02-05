FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . /workspace
RUN mvn -DskipTests -pl Example/honeycomb-example -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/Example/honeycomb-example/target/honeycomb-example-0.1.0.jar /app/app.jar
EXPOSE 8080 9091 9092 9093 9191 9192 9193
ENTRYPOINT ["java","-jar","/app/app.jar"]
