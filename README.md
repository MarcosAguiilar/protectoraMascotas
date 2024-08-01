# Protectora de Mascotas - API Rest

## Descripción

Este proyecto es una API Rest para una protectora de mascotas, específicamente para perros. La API está construida utilizando Spring Boot y Maven, y actualmente se encuentra en modo de pruebas. Cada vez que se inicia la aplicación, se inyectan las mascotas que están definidas en el archivo `src/main/resources/import.sql`.

## Configuración

### Configuración de la base de datos

La aplicación utiliza una base de datos H2 en memoria para modo de pruebas. La configuración de la base de datos se puede encontrar en el archivo `src/main/resources/application.properties`.

### Inyección de Datos

Cada vez que se inicia la aplicación, se inyectan los datos de las mascotas desde el archivo `src/main/resources/import.sql`. Esto es útil para mantener un conjunto de datos de prueba constante durante el desarrollo y las pruebas.

Si deseas mantener los datos entre reinicios de la aplicación, debes modificar la configuración de Hibernate en el archivo `src/main/resources/application.properties`.

### Cambiar el modo de inyección de datos

Para cambiar la configuración de inyección de datos, abre el archivo `src/main/resources/application.properties` y modifica la propiedad `spring.jpa.hibernate.ddl-auto`.

- Modo de pruebas (recomendado para desarrollo):
    ```properties
    spring.jpa.hibernate.ddl-auto=create-drop
    ```

- Modo persistente (recomendado para producción):
    ```properties
    spring.jpa.hibernate.ddl-auto=update
    ```

## Métodos del MascotaController

El `MascotaController` es un controlador REST en una aplicación Spring Boot que gestiona las operaciones relacionadas con las mascotas. Este controlador proporciona varias operaciones a través de diferentes endpoints:

- **`GET /api/mascotas/{id}`**: Este método devuelve una mascota específica por su ID. Si la mascota existe, se retorna con un estado HTTP 200 (OK), de lo contrario, se retorna un estado HTTP 404 (NOT FOUND).

- **`GET /api/mascotas/nombre/{name}`**: Este método permite buscar mascotas por su nombre. Si no se encuentran mascotas con el nombre especificado, se retorna un estado HTTP 204 (NO CONTENT). Si se encuentran, se retorna una lista de mascotas con un estado HTTP 200 (OK).

- **`POST /api/mascotas`**: Este método permite agregar una nueva mascota a la base de datos. La información de la mascota se recibe en formato JSON en el cuerpo de la solicitud. Si la operación es exitosa, se retorna la mascota creada con un estado HTTP 201 (CREATED).

- **`GET /api/mascotas`**: Este método devuelve todas las mascotas registradas en la base de datos.

- **`GET /api/mascotas/paged20`**: Este método devuelve las 20 mascotas con edad más joven registradas en la base de datos.

- **`GET /api/mascotas/paged/{pag}`**: Este método permite paginar los resultados, devolviendo un conjunto de mascotas basado en el número de página especificado en la URL.

- **`DELETE /api/mascotas/delete/{id}`**: Este método permite eliminar una mascota específica por su ID. Si la operación es exitosa, se retorna un estado HTTP 204 (NO CONTENT).

