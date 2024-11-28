# Bingo Backend

Este repositorio contiene el backend para el proyecto **Bingo Buda**, desarrollado con Java Spring Boot.

---

## Requisitos Previos

1. **Java 17:** Asegúrate de tener instalado Java 17 o superior.
2. **Maven:** Instalado para manejar dependencias.
3. **MongoDB:** Debes tener un servidor MongoDB configurado. Para usar Docker Compose, el backend ya está configurado para conectarse al contenedor de MongoDB.

---

## Configuración del Proyecto

### Configuración de la Base de Datos

Edita el archivo `src/main/resources/application.properties` si deseas cambiar los datos de conexión a MongoDB:

```properties
spring.data.mongodb.uri=mongodb://userbuda:budapass@localhost:27017/bingobudabd?authSource=admin
```

### Ejecutar Localmente

1. Clona el repositorio:
   ```bash
   git clone https://github.com/<TU_USUARIO>/bingo-backend.git
   cd bingo-backend
   ```

2. Construye y ejecuta el proyecto:
   ```bash
   mvn clean package
   java -jar target/bingo-backend-0.0.1-SNAPSHOT.jar
   ```

El backend estará disponible en [http://localhost:5000](http://localhost:5000).

---

## Usar Docker para Backend

### Construir la Imagen

1. Desde el directorio del backend, construye la imagen Docker:
   ```bash
   docker build -t bingo-backend .
   ```

2. Ejecuta el contenedor:
   ```bash
   docker run -p 5000:5000 --name bingo-backend bingo-backend
   ```

El backend estará disponible en [http://localhost:5000](http://localhost:5000).

---

## Tecnologías

- **Java 17**
- **Spring Boot** 3.4.0
- **MongoDB**

---

© 2024 Bingo Gran Buda. Todos los derechos reservados.

