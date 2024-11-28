# Etapa 1: Construcción
FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /app

# Copiar archivos necesarios para la construcción
COPY pom.xml ./
COPY src ./src

# Construir el proyecto
RUN mvn clean package -DskipTests

# Etapa 2: Imagen final
FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copiar el archivo JAR desde la etapa de construcción
COPY --from=build /app/target/*.jar app.jar

# Puerto expuesto para la aplicación
EXPOSE 5000

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]
