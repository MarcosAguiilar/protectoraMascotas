# Protectora de Mascotas - API Rest

## Descripción

Este proyecto es una API Rest para una protectora de mascotas, específicamente para perros. La API está construida utilizando Spring Boot y Maven, y actualmente se encuentra en modo de pruebas. Cada vez que se inicia la aplicación, se inyectan las mascotas que están definidas en el archivo `src/main/resources/import.sql`.

## Configuración

### Configuración de la base de datos

La aplicación utiliza una base de datos H2 en memoria para modo de pruebas. La configuración de la base de datos se puede encontrar en el archivo `src/main/resources/application.properties`.

### Inyección de Datos

Cada vez que se inicia la aplicación, se inyectan los datos de las mascotas desde el archivo `src/main/resources/import.sql`. Esto es útil para mantener un conjunto de datos de prueba constante durante el desarrollo y las pruebas.

La base de datos sigue siendo temporal en todos los perfiles. Cambiar solamente
`ddl-auto` a `update` no conserva una base H2 en memoria entre reinicios. La
persistencia de producción queda pendiente y no forma parte de la mejora de seguridad.

## Seguridad básica

Se utiliza Spring Security con HTTP Basic y una única cuenta en memoria. No hay
registro de usuarios, JWT ni jerarquías de roles. El proyecto utiliza Java 17 y
Spring Boot 3.5.16; las versiones de seguridad y persistencia las gestiona el BOM
de Spring Boot. La documentación utiliza Springdoc OpenAPI 2.8.17.

| Recurso | Acceso |
| --- | --- |
| Los cinco endpoints GET de `/api/mascotas` | Público |
| `POST /api/mascotas` | Autenticación y token CSRF |
| `DELETE /api/mascotas/delete/{id}` | Autenticación y token CSRF |
| `GET /api/csrf` | Autenticación; devuelve el token CSRF |
| Swagger UI, `/v3/api-docs` y `/v3/api-docs.yaml` | Autenticación |
| H2 Console | Solo perfil `dev`, acceso local y autenticación |
| Otras rutas | Denegadas por defecto |

### Credenciales y perfiles

Configura `API_USERNAME` y `API_PASSWORD` mediante variables de entorno. Ambas son
obligatorias en todos los perfiles, incluido el perfil por defecto. Si falta una o
contiene solo espacios, el arranque falla con un mensaje que no muestra sus valores.
No existe una contraseña de respaldo ni se utiliza el usuario automático de Spring.
La contraseña de la cuenta se codifica con BCrypt al construir el almacén en memoria.

- `dev`: activar explícitamente con `SPRING_PROFILES_ACTIVE=dev`. Escucha en
  `127.0.0.1`, habilita H2 Console y prohíbe conexiones remotas a la consola.
- `prod`: activar con `SPRING_PROFILES_ACTIVE=prod`. Deshabilita H2 Console y
  exige cookies de sesión seguras, transmitidas por HTTPS.
- Sin perfil: H2 Console permanece deshabilitada y las credenciales siguen siendo
  obligatorias. Nunca se activa `dev` automáticamente.

Intentar habilitar H2 Console sin `dev`, o cuando también esté activo `prod`, impide
el arranque. El perfil `dev` no debe utilizarse en servidores públicos.

En IntelliJ, selecciona JDK 17 tanto para el proyecto como para Maven. Introduce las
variables en la configuración de ejecución local, sin compartirlas ni exportarlas
al repositorio. En un despliegue, utiliza el gestor de secretos/variables del proveedor.
No guardes contraseñas en archivos `.properties`, comandos versionados ni archivos `.env`.

Comprueba el JDK utilizado por Maven con `.\mvnw.cmd -version`: declarar
`java.version=17` en el POM no selecciona el ejecutable de Java. Configura `JAVA_HOME`
para apuntar al JDK 17. La entidad `Mascota` utiliza getters y setters explícitos,
sin Lombok, para que la lectura y escritura de JSON no dependan del procesamiento
de anotaciones del compilador.

Ejemplo de arranque local en PowerShell, solicitando las credenciales sin incluirlas
en el historial de comandos:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:API_USERNAME = Read-Host 'Usuario de la API'
$env:API_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Contraseña de la API' -AsSecureString)).Password
.\mvnw.cmd spring-boot:run
```

`DB_USERNAME` y `DB_PASSWORD` configuran únicamente la conexión H2; no son las
credenciales de la API. Para la base temporal local, los valores predeterminados son
`sa` y contraseña vacía. La consola solicita esas credenciales de base de datos
después del control de acceso HTTP Basic.

### CSRF y operaciones de escritura

HTTP Basic no cifra las credenciales: en producción debe utilizarse exclusivamente
HTTPS, también cuando TLS termine en el proxy del proveedor. Los navegadores pueden
reenviar automáticamente credenciales Basic, por lo que se conserva la protección
CSRF de Spring Security.

Para escribir desde Postman, un script o un navegador:

1. Solicita `GET /api/csrf` con autenticación Basic.
2. Conserva la cookie de sesión devuelta.
3. Envía POST o DELETE con Basic, la misma cookie y la cabecera indicada por
   `headerName`, cuyo valor será el campo `token` de la respuesta.

Ejemplo local en PowerShell, con las variables de credenciales ya configuradas:

```powershell
$baseUrl = 'http://127.0.0.1:8080' # En producción, utilizar la URL HTTPS.
$basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${env:API_USERNAME}:${env:API_PASSWORD}"))
$apiHeaders = @{ Authorization = "Basic $basic" }
$csrf = Invoke-RestMethod "$baseUrl/api/csrf" -Headers $apiHeaders -SessionVariable apiSession
$apiHeaders[$csrf.headerName] = $csrf.token
$mascota = Invoke-RestMethod "$baseUrl/api/mascotas" -Method Post -Headers $apiHeaders -WebSession $apiSession -ContentType 'application/json' -Body '{"nombre":"Luna","peso":10}'
Invoke-RestMethod "$baseUrl/api/mascotas/delete/$($mascota.id)" -Method Delete -Headers $apiHeaders -WebSession $apiSession
```

No registres en logs las cabeceras de autorización, cookies ni tokens. Si cambia o
caduca la sesión, solicita un token nuevo. Swagger permanece accesible con Basic,
pero sus operaciones de escritura también necesitan cumplir este flujo CSRF.

- `401 Unauthorized`: falta autenticación o las credenciales son incorrectas,
  cuando la petición supera el control CSRF previo.
- `403 Forbidden`: token CSRF ausente/inválido o ruta denegada. Una petición
  anónima de escritura sin token también obtiene 403, porque CSRF se comprueba antes.

La única excepción CSRF es la consola H2 local, que no integra los tokens de Spring.
La API mantiene CSRF habilitado también en `dev`.

### Pruebas

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

Las pruebas generan credenciales efímeras y no necesitan secretos reales. Comprueban
consultas públicas, rechazos 401/403, altas y borrados autenticados con un token real,
restricciones de H2 y fallos de arranque ante una configuración insegura. Las pruebas
HTTP utilizan MockMvc con los filtros reales de seguridad y la base H2 de pruebas.

## Segunda etapa: Spring Boot 3.5.16

La primera etapa migró desde 2.4.5 a 2.7.18. Esta segunda etapa actualiza desde
2.7.18 a 3.5.16, manteniendo Java 17, los modelos JSON y las reglas de acceso.
No migra todavía a Spring Boot 4. Las dependencias se alinean con el parent:

| Componente | Antes | Ahora |
| --- | --- | --- |
| Spring Framework | 5.3.31 | 6.2.19 |
| Spring Security | 5.7.11 | 6.5.11 |
| Spring Data JPA | 2.7.18 | 3.5.13 |
| Hibernate | 5.6.15.Final | 6.6.53.Final |
| H2 | 2.1.214 | 2.3.232 |
| Jackson | 2.13.5 | 2.21.4 |
| Tomcat | 9.0.83 | 10.1.55 |
| JUnit Jupiter | 5.8.2 | 5.12.2 |

El plugin de Spring Boot pasa a 3.5.16, el compilador Maven a 3.14.1, el plugin
JAR a 3.4.2 y Surefire a 3.5.6. El Wrapper continúa en Maven 3.9.7.

### Jakarta y seguridad

Las anotaciones de `Mascota` utilizan `jakarta.persistence` en lugar de
`javax.persistence`, y `DispatcherType` procede de `jakarta.servlet`. No cambian
los campos, sus nombres JSON, sus tipos Java ni las columnas de la entidad.

La configuración de seguridad ya no hereda de `WebSecurityConfigurerAdapter`.
Declara un bean `SecurityFilterChain` y utiliza `authorizeHttpRequests` y
`requestMatchers`. Se mantienen las consultas GET públicas, Basic, las credenciales
de entorno obligatorias, el rechazo por defecto y las restricciones de H2.

Spring Security 6 carga el token CSRF de forma diferida y lo enmascara mediante
XOR para mitigar BREACH. Se conserva ese comportamiento seguro predeterminado.
El endpoint `/api/csrf` devuelve el token en los mismos campos JSON y el cliente
continúa enviándolo con la misma cookie de sesión y la cabecera indicada.
No debe interpretarse ni reconstruirse el token; debe tratarse como una cadena opaca.
La excepción CSRF de la consola H2 sigue limitada al perfil local `dev` sin `prod`.

### Documentación OpenAPI

Springfox ya se retiró en la primera etapa. Ahora
`org.springdoc:springdoc-openapi-ui:1.8.0` se sustituye por
`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17`, compatible con Boot 3.5.
Springdoc 2.8 genera OpenAPI 3.1 por defecto. Para conservar el documento 3.0.1
publicado anteriormente, se configura `springdoc.api-docs.version=OPENAPI_3_0`.

- Interfaz: `/swagger-ui.html`, que redirige a `/swagger-ui/index.html`.
- Especificación OpenAPI 3: `/v3/api-docs` y `/v3/api-docs.yaml`.
- Configuración de la interfaz: `/v3/api-docs/swagger-config`.
- La antigua especificación Swagger 2 `/v2/api-docs` se retira.

Todas estas rutas y sus recursos requieren HTTP Basic. El documento distingue
las consultas públicas de POST, DELETE y `/api/csrf`, que requieren autenticación.
POST y DELETE documentan también la cabecera obligatoria `X-CSRF-TOKEN`: debe
obtenerse mediante `/api/csrf` y utilizarse con la misma cookie de sesión. Cambiar
de generador de documentación no desactiva CSRF ni altera los permisos de la API.

### Compatibilidad de persistencia y rutas

`MascotaRepository` extiende `JpaRepository`: Spring Data 3 separa las interfaces
CRUD y de paginación, y JpaRepository proporciona ambas. Se mantienen las búsquedas
por nombre exacto y por fecha de nacimiento. La carga de los 49 registros de
`import.sql`, las fechas, los booleanos y los identificadores IDENTITY se verifican
con Hibernate 6 y H2 2.3, sin modo LEGACY ni cambios de SQL.

En Spring Data 3, `deleteById` ignora identificadores inexistentes. El repositorio
conserva explícitamente la excepción `EmptyResultDataAccessException` de la versión
anterior y su transacción. Así se evita cambiar silenciosamente la respuesta HTTP
500 anterior a 204. Mejorar ese caso a 404 queda para una decisión posterior sobre
el contrato REST.

Spring Framework 6 deja de aceptar automáticamente las barras finales. Los cinco
mappings GET declaran también la variante terminada en `/`, para mantener las
consultas que ya funcionaban con Boot 2.7. No se reactiva esa opción globalmente
ni se amplían los permisos de escritura.

Se conserva la respuesta de paginación directa y sus metadatos. Spring Data advierte
que serializar PageImpl directamente no garantiza un formato estable entre versiones;
por ello se añaden pruebas de contrato y se compara el JSON real antes y después.
Adoptar otro DTO paginado cambiaría el contrato y queda fuera de esta migración.

Se mantienen `create-drop`, la base en memoria y los perfiles `dev` y `prod`.
Esta etapa no resuelve la persistencia duradera de producción ni cambia las fechas
del modelo a LocalDate. Las pruebas de repositorio fuerzan flush y limpian el contexto
JPA para comprobar los valores leídos realmente de la base de datos.

## Métodos del MascotaController

El `MascotaController` es un controlador REST en una aplicación Spring Boot que gestiona las operaciones relacionadas con las mascotas. Este controlador proporciona varias operaciones a través de diferentes endpoints:

- **`GET /api/mascotas/{id}`**: Este método devuelve una mascota específica por su ID. Si la mascota existe, se retorna con un estado HTTP 200 (OK), de lo contrario, se retorna un estado HTTP 404 (NOT FOUND).

- **`GET /api/mascotas/nombre/{name}`**: Este método permite buscar mascotas por su nombre. Si no se encuentran mascotas con el nombre especificado, se retorna un estado HTTP 204 (NO CONTENT). Si se encuentran, se retorna una lista de mascotas con un estado HTTP 200 (OK).

- **`POST /api/mascotas`**: Este método permite agregar una nueva mascota a la base de datos. La información de la mascota se recibe en formato JSON en el cuerpo de la solicitud. Si la operación es exitosa, se retorna la mascota creada con un estado HTTP 201 (CREATED).

- **`GET /api/mascotas`**: Este método devuelve todas las mascotas registradas en la base de datos.

- **`GET /api/mascotas/paged20`**: Este método devuelve las 20 mascotas con edad más joven registradas en la base de datos.

- **`GET /api/mascotas/paged/{pag}`**: Este método permite paginar los resultados, devolviendo un conjunto de mascotas basado en el número de página especificado en la URL.

- **`DELETE /api/mascotas/delete/{id}`**: Este método permite eliminar una mascota específica por su ID. Si la operación es exitosa, se retorna un estado HTTP 204 (NO CONTENT).

