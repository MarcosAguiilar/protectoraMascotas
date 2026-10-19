# Protectora de Mascotas - API Rest

## Descripción

Este proyecto es una API REST para una protectora de mascotas, específicamente para perros.
Utiliza Spring Boot 4.1.1, Java 17, PostgreSQL y Flyway. H2 y las 49 mascotas de
demostración se utilizan exclusivamente en los tests; la base de la aplicación
empieza vacía y conserva los datos entre reinicios.

## Arquitectura

El flujo de una petición de mascotas es `MascotaController -> MascotaService ->
MascotaRepository`. El controlador interpreta HTTP y decide las respuestas y sus
códigos, valida los DTOs de entrada y utiliza un mapper explícito para convertir
entre DTOs y entidades. El servicio reúne las operaciones de consulta, registro, eliminación y
paginación. El repositorio realiza el acceso a datos con Spring Data JPA.

Controlador y servicio reciben sus dependencias mediante constructor y las guardan
en campos `final`. El servicio es una clase concreta, sin interfaz adicional.
Sus consultas utilizan transacciones de solo lectura; el registro y la eliminación
utilizan transacciones de escritura. Los tamaños de página siguen siendo cinco
elementos y veinte en la consulta de las mascotas más jóvenes. La página completa
se devuelve con sus metadatos, conservando el contrato JSON existente.

`MascotaServiceTests` utiliza JUnit y Mockito sin arrancar Spring ni una base de
datos. Las pruebas HTTP y de repositorio existentes comprueban la integración real,
incluidos HTTP Basic, CSRF y la conservación de los códigos de respuesta.

## DTOs y validación de mascotas

`Mascota` es la entidad JPA que representa la tabla y se utiliza en el servicio y
el repositorio. La API utiliza dos records independientes de las anotaciones JPA:

- `CrearMascotaRequest`: los seis campos que acepta el alta; no contiene `id`.
- `MascotaResponse`: los siete campos que devuelve la API, incluido el ID generado.

`MascotaMapper` convierte explícitamente los campos. Cada alta construye una
entidad nueva con ID nulo. Las fechas se copian para no compartir un objeto Date
mutable entre DTO y entidad. Se conserva Date para mantener el formato y la
interpretación de fechas existentes; un record no convierte Date en un tipo inmutable.
No se añaden MapStruct, interfaces ni cambios de tablas o columnas.

Las respuestas de lectura y la respuesta de creación conservan estas propiedades:
`id`, `nombre`, `fechaNac`, `raza`, `peso`, `tiene_chip` y `url_foto`. Los componentes
Java `tieneChip` y `urlFoto` se vinculan a sus nombres JSON mediante `@JsonProperty`.
Las fechas mantienen el formato UTC con milisegundos y offset, por ejemplo
`2020-05-01T12:00:00.000+00:00`. Se conservan los campos opcionales nulos y los
valores predeterminados anteriores, incluido `tiene_chip=false` si se omite.

Las listas mantienen su estructura. En la consulta paginada se aplica `Page.map`
para convertir el contenido sin perder `totalElements`, `totalPages`, `pageable`
ni los demás metadatos. Se conservan las rutas, barras finales y estados GET 200,
204 cuando una búsqueda por nombre no encuentra resultados y 404 para un ID inexistente.

### Cambios intencionados del contrato de escritura

El POST utiliza `@Valid` y Jakarta Bean Validation mediante
`spring-boot-starter-validation`, con la versión gestionada por Spring Boot 4.1.1:

- `nombre` es obligatorio: ausente, nulo, vacío o compuesto solo por espacios
  produce HTTP 400. No se recortan ni normalizan los nombres válidos.
- `peso` es opcional: ausente o nulo se acepta; si se proporciona, debe ser
  estrictamente positivo. Cero o valores negativos producen HTTP 400.
- Un `id` enviado en el JSON se ignora explícitamente con `@JsonIgnoreProperties`.
  No actualiza registros existentes ni selecciona el identificador del alta. El
  servidor genera un ID y lo devuelve con HTTP 201. POST no es una operación de actualización.

No se añaden validaciones de raza, fecha, URL ni chip en esta etapa. Una solicitud
inválida no inserta filas. Los rechazos de validación devuelven ProblemDetail,
con los campos inválidos y mensajes seguros, como se describe a continuación.

La autenticación Basic y CSRF se comprueban antes de la validación del cuerpo.
Un 400 por validación exige haber superado los controles de seguridad; las peticiones
no autenticadas o sin token válido conservan sus rechazos 401/403. DELETE devuelve
204 al eliminar una mascota existente y 404 si no existe. El DTO de respuesta
conserva el ID de la mascota.

Ejemplo de cuerpo válido de creación (además requiere Basic, cookie y token CSRF):

```json
{
  "nombre": "Luna",
  "fechaNac": "2020-05-01T12:00:00.000+00:00",
  "raza": "Mestiza",
  "peso": 12,
  "tiene_chip": true,
  "url_foto": "www.example.com/luna.jpg"
}
```

Las pruebas unitarias verifican las restricciones y el mapeo. Las pruebas de
integración comparan el JSON de los cinco GET con la representación anterior,
comprueban fechas y valores opcionales y verifican que enviar un ID no modifica
otra mascota. Las pruebas previas de servicio, persistencia y seguridad se conservan.

## Gestión global de errores HTTP

`GlobalExceptionHandler` utiliza `@RestControllerAdvice` y extiende
`ResponseEntityExceptionHandler`. Centraliza la traducción de excepciones conocidas
a `ProblemDetail` (RFC 9457), con `Content-Type: application/problem+json`.
Los controladores conservan sus DTOs de éxito y ya no construyen errores de existencia.

| Situación | Estado HTTP |
| --- | --- |
| Consultar o eliminar un ID de mascota inexistente | 404 |
| Nombre inválido o peso no positivo | 400 |
| Cuerpo vacío, JSON malformado o tipos JSON incorrectos | 400 |
| ID o página no numéricos, decimales o fuera del rango entero admitido | 400 |
| Página negativa o superior a 429496729 | 400 |
| Eliminar una mascota existente con Basic y CSRF válidos | 204, sin cuerpo |

El servicio lanza `MascotaNoEncontradaException` si no encuentra la mascota, antes
de intentar eliminarla. El advice convierte esa excepción a HTTP 404. Ejemplo de
respuesta de `GET /api/mascotas/999999`:

```json
{
  "type": "about:blank",
  "title": "Mascota no encontrada",
  "status": 404,
  "detail": "No existe una mascota con ID 999999.",
  "instance": "/api/mascotas/999999"
}
```

Se incluye explícitamente `type: about:blank`, el tipo genérico de RFC 9457.
`title` resume el error, `status` reproduce el código HTTP,
`detail` explica el caso e `instance` identifica la ruta de la petición.
En errores de validación se añade `errors`, por ejemplo:

```json
{
  "type": "about:blank",
  "title": "Datos no válidos",
  "status": 400,
  "detail": "Hay datos de entrada no válidos.",
  "instance": "/api/mascotas",
  "errors": [
    {"field": "nombre", "message": "El nombre es obligatorio y no puede contener solo espacios"}
  ]
}
```

No se devuelven valores rechazados, mensajes del parser, trazas, SQL ni nombres
de clases internas. Solo se traducen excepciones conocidas; no hay un manejador
genérico de `Exception` que convierta fallos inesperados en errores 400.
Los fallos internos conservan el 500 y el manejo de errores de Spring Boot, con
mensaje, excepción, errores de binding y traza deshabilitados explícitamente,
también si el cliente solicita `trace=true` o `message=true`.
Ese fallback 500 y las respuestas de seguridad no utilizan este nuevo contrato ProblemDetail.

La seguridad se evalúa antes del controlador y conserva HTTP 401 para peticiones
no autenticadas que superan CSRF, y 403 para CSRF inválido o acceso prohibido.
No se modifica la cadena de seguridad ni el formato de sus respuestas.

Las páginas siguen empezando en cero y contienen cinco mascotas. Se rechazan
índices que desbordarían el offset entero admitido por JPA; una página admitida
sin resultados conserva HTTP 200, contenido vacío y sus metadatos.
La búsqueda por nombre sin resultados conserva 204. Los JSON de éxito, fechas,
rutas y estructura de paginación no cambian.

Los cambios intencionados son el cuerpo ProblemDetail del GET inexistente,
el DELETE inexistente (antes error interno, ahora 404), las páginas inválidas
(antes algunas causaban 500, ahora 400) y el cuerpo consistente de los errores 400.
`ApiErrorIntegrationTests` comprueba esos contratos, la ausencia de detalles
sensibles y la prioridad de seguridad con los filtros reales y H2.
`GlobalExceptionHandlerTests` comprueba las traducciones y que fallos inesperados
de programación o persistencia se propagan.

## Configuración

### Configuración de la base de datos

La aplicación utiliza PostgreSQL. `application.properties` exige `DB_URL` (URL JDBC),
`DB_USERNAME` y `DB_PASSWORD`, sin credenciales de respaldo. `API_USERNAME` y
`API_PASSWORD` siguen siendo obligatorias e independientes de la base de datos.

### Flyway y esquema

La migración `src/main/resources/db/migration/V1__crear_tabla_mascota.sql` crea la
tabla existente, con ID `BIGINT GENERATED BY DEFAULT AS IDENTITY`, cadenas de 255
caracteres, peso `BIGINT`, chip booleano y fecha `TIMESTAMP(6) WITHOUT TIME ZONE`.
Conserva los nombres y la nulabilidad del modelo JPA. No contiene datos de ejemplo.

Flyway se ejecuta antes de JPA y registra las versiones y sus checksums en
`flyway_schema_history`. En el siguiente arranque no vuelve a ejecutar V1. Para
cambiar el esquema se añade V2, V3, etc.; no se modifica una migración aplicada.
Hibernate utiliza `ddl-auto=validate`: comprueba el esquema sin crearlo ni borrarlo.
La inicialización SQL de Spring está deshabilitada y ya no existe `import.sql`.

`clean` está deshabilitado y `baseline-on-migrate=false`. Si la base ya contiene
tablas sin historial Flyway, el arranque se detiene: antes de adoptarla hay que
hacer una copia, comparar el esquema y planificar su incorporación. No habilitar
baseline ni borrar tablas para silenciar ese error. Esta V1 presupone una base nueva.

### PostgreSQL local con Docker Compose e IntelliJ

Requisitos: JDK 17 y Docker Desktop con Docker Compose. Ejecutar desde la raíz:

```powershell
$env:DB_NAME = 'protectora_mascotas'
$env:DB_PORT = '5432'
$env:DB_USERNAME = Read-Host 'Usuario PostgreSQL local'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Contraseña PostgreSQL local' -AsSecureString)).Password
docker compose up -d --wait
docker compose ps
```

Compose inicia únicamente PostgreSQL 18, publica el puerto en `127.0.0.1` y espera
su healthcheck `pg_isready`. La imagen se fija a una versión mayor para evitar
saltos automáticos de formato del clúster. Las credenciales son obligatorias; no
se incluyen secretos en el YAML. No ejecutar `docker compose config` para compartir
su salida: la configuración expandida puede mostrar la contraseña.

En la configuración Run/Debug de IntelliJ, seleccionar JDK 17 y perfil `dev`.
Introducir localmente `DB_USERNAME`, `DB_PASSWORD`, `API_USERNAME`, `API_PASSWORD`,
y, si se cambian, `DB_NAME` y `DB_PORT`. No compartir la configuración con secretos.
Las variables de una terminal no se transmiten automáticamente a un IntelliJ ya abierto.
El perfil `dev` construye `jdbc:postgresql://localhost:5432/protectora_mascotas`
por defecto; puede sobrescribirse con `DB_URL`. Usar `localhost`, porque la API
corre en Windows; el hostname `postgres` solo sirve entre contenedores de Compose.
Si 5432 está ocupado por PostgreSQL instalado localmente, elegir otro `DB_PORT`,
por ejemplo 5433, tanto para Compose como para IntelliJ.

También se puede ejecutar desde la misma terminal que inició Compose:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:API_USERNAME = Read-Host 'Usuario API'
$env:API_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Contraseña API' -AsSecureString)).Password
.\mvnw.cmd spring-boot:run
```

La aplicación escucha en `127.0.0.1:8080`; Swagger sigue requiriendo Basic y las
escrituras necesitan el token de `/api/csrf` con la misma cookie de sesión.

```powershell
docker compose stop           # Detiene, conservando el contenedor y el volumen.
docker compose start --wait   # Arranca lo que ya existe.
docker compose restart postgres
docker compose down           # Retira contenedor/red, conserva el volumen.
docker compose up -d --wait   # Recrea el contenedor usando el mismo volumen.
```

El volumen `postgres_data` conserva los archivos fuera de la capa del contenedor.
PostgreSQL 18 utiliza el montaje `/var/lib/postgresql`; no confundirlo con el de
versiones anteriores. Mantener el mismo nombre de proyecto Compose para reutilizar
el volumen. **No ejecutar `docker compose down -v`, `docker volume rm` ni cambiar
la versión mayor de PostgreSQL sobre un volumen existente sin backup y un plan.**
Un volumen persistente no sustituye una copia de seguridad. Las variables
`POSTGRES_*` inicializan usuarios/base solo si el volumen está vacío; cambiar
`DB_PASSWORD` no cambia la contraseña de un clúster existente.

Comprobación manual adicional que requiere Docker:

1. Arrancar Compose y la API; comprobar V1 aplicada y `GET /api/mascotas` vacío en una base nueva.
2. Crear una mascota con Basic y un token CSRF real; guardar el ID generado.
3. Ejecutar `docker compose restart postgres` y esperar a que el servicio esté healthy.
4. Consultar el mismo ID; debe devolver los mismos datos. Repetir tras `down` y `up -d --wait`.
5. Reiniciar también la API: Flyway debe validar sin repetir V1 y la mascota debe seguir existiendo.

No se ejecutan esos comandos contra bases o volúmenes existentes automáticamente.
La cuenta creada por la imagen oficial es administrativa y se limita al desarrollo
local; en producción se deben provisionar credenciales y permisos apropiados,
conexión protegida y backups. Configurar perfil `prod`, URL JDBC explícita y las
variables obligatorias mediante el entorno del servidor.

### Pruebas sin Docker

`application-test.properties` y el script
`src/test/resources/db/testdata/R__mascotas_demo.sql` existen solo en el classpath
de pruebas. El perfil `test` usa H2 en memoria, la misma V1 estructural y una
migración repetible con las 49 mascotas de prueba. Los tests HTTP activan además
`prod` para conservar las comprobaciones de cookies y restricciones de seguridad.
El JAR de producción no contiene H2, consola H2, perfil test ni datos de ejemplo.

La migración utiliza SQL estándar admitido por PostgreSQL y H2, sin modo LEGACY ni
modo de emulación PostgreSQL. H2 permite comprobar V1, CRUD y contratos rápidamente,
pero no sustituye la validación real en PostgreSQL. `FlywayMigrationTests` comprueba
creación de tabla vacía e ID, reejecución sin pérdida de datos, rechazo de esquemas
existentes sin historial y bloqueo de clean. Los tests de JPA verifican además
que Hibernate valida el esquema creado por Flyway.

```powershell
.\mvnw.cmd clean verify
```

### Resultado de esta etapa

La verificación con Java 17 pasó de 122 a 126 pruebas: `clean verify` termina con
0 fallos, 0 errores y 0 omitidas. Se inspeccionó el JAR para confirmar que excluye
H2, su consola, el perfil test y los datos de demostración.

Además se verificó el ejecutable en un clúster temporal aislado de PostgreSQL 18.6:
V1 aplicada, validación JPA, lista inicial vacía, alta con Basic y CSRF reales,
conservación de los siete campos tras reiniciar PostgreSQL y la API, historial V1
sin duplicados, OpenAPI y eliminación. Se conservaron 401/403 y 404 ProblemDetail.
El clúster de prueba se detuvo al terminar; no se tocaron bases existentes.

Docker no está disponible en el equipo de esta verificación. La ejecución de
Compose, el healthcheck y la persistencia de su volumen quedan pendientes de los
pasos manuales anteriores. La prueba nativa no sustituye esa comprobación de Docker.

## Seguridad básica

Se utiliza Spring Security con HTTP Basic y una única cuenta en memoria. No hay
registro de usuarios, JWT ni jerarquías de roles. El proyecto utiliza Java 17 y
Spring Boot 4.1.1; las versiones de seguridad y persistencia las gestiona el BOM
de Spring Boot. La documentación utiliza Springdoc OpenAPI 3.1.1.

| Recurso | Acceso |
| --- | --- |
| Los cinco endpoints GET de `/api/mascotas` | Público |
| `POST /api/mascotas` | Autenticación y token CSRF |
| `DELETE /api/mascotas/delete/{id}` | Autenticación y token CSRF |
| `GET /api/csrf` | Autenticación; devuelve el token CSRF |
| Swagger UI, `/v3/api-docs` y `/v3/api-docs.yaml` | Autenticación |
| H2 Console | Ausente del JAR de la aplicación; solo dependencia de tests |
| Otras rutas | Denegadas por defecto |

### Credenciales y perfiles

Configura `API_USERNAME` y `API_PASSWORD` mediante variables de entorno. Ambas son
obligatorias en todos los perfiles, incluido el perfil por defecto. Si falta una o
contiene solo espacios, el arranque falla con un mensaje que no muestra sus valores.
No existe una contraseña de respaldo ni se utiliza el usuario automático de Spring.
La contraseña de la cuenta se codifica con BCrypt al construir el almacén en memoria.

- `dev`: activar explícitamente con `SPRING_PROFILES_ACTIVE=dev`. Escucha en
  `127.0.0.1`, utiliza PostgreSQL local y mantiene H2 Console deshabilitada.
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

`DB_USERNAME` y `DB_PASSWORD` configuran PostgreSQL; no son las credenciales de la
API. No tienen valores de respaldo. H2 usa una cuenta efímera de pruebas y no está
incluida en el ejecutable de la aplicación.

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

Se conserva la configuración de seguridad, incluida su restricción histórica para
la consola H2 local. La consola está deshabilitada y no se empaqueta en la aplicación.
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

## Tecnologías implementadas

La API utiliza Spring Boot 4.1.1 y Java 17. Las versiones siguientes se alinean
con el parent de Maven y han sido comprobadas en el árbol de dependencias:

| Componente | Versión |
| --- | --- |
| Spring Framework | 7.0.9 |
| Spring Security | 7.1.1 |
| Spring Data JPA | 4.1.1 |
| Hibernate | 7.4.5.Final |
| H2 | 2.4.240 |
| Flyway | 12.4.0 |
| Driver JDBC PostgreSQL | 42.7.13 |
| Jackson de la API | 3.1.5 |
| Jackson interno de Swagger Core | 2.21.5 (transitivo) |
| Tomcat | 11.0.24 |
| JUnit Jupiter | 6.0.3 |

El plugin de Spring Boot utiliza 4.1.1, el compilador Maven 3.15.0, el plugin
JAR 3.5.1 y Surefire 3.5.6. El Wrapper utiliza Maven 3.9.7.

### Módulos WebMVC, JSON y pruebas

El servidor utiliza `spring-boot-starter-webmvc` y `spring-boot-starter-flyway`.
El driver PostgreSQL y `flyway-database-postgresql` proporcionan soporte para la base.
H2 y `spring-boot-h2console` tienen scope test. Las pruebas incorporan `spring-boot-starter-webmvc-test` y
`spring-boot-starter-security-test`, incluidos los filtros reales de seguridad.

Se utiliza Jackson 3, con los tipos `tools.jackson.databind.JsonNode` y
`tools.jackson.databind.json.JsonMapper` en las pruebas. La propiedad
`spring.jackson.use-jackson2-defaults=true` conserva los valores predeterminados
JSON de Boot 3 usando Jackson 3, sin introducir un mapper Jackson 2. Las pruebas
comprueban los nombres de campos, el formato UTC de fechas, los ID y la paginación.
Swagger Core incorpora Jackson 2 como dependencia interna transitiva de Springdoc;
coexiste con Jackson 3 sin sustituir el mapper HTTP de la aplicación.

### Jakarta y seguridad

Las anotaciones de `Mascota` utilizan `jakarta.persistence` en lugar de
`javax.persistence`, y `DispatcherType` procede de `jakarta.servlet`. No cambian
los campos, sus nombres JSON, sus tipos Java ni las columnas de la entidad.

La configuración de seguridad ya no hereda de `WebSecurityConfigurerAdapter`.
Declara un bean `SecurityFilterChain` y utiliza `authorizeHttpRequests` y
`requestMatchers`. Se mantienen las consultas GET públicas, Basic, las credenciales
de entorno obligatorias, el rechazo por defecto y las restricciones de H2.

Spring Security 7 carga el token CSRF de forma diferida y lo enmascara mediante
XOR para mitigar BREACH. Se conserva ese comportamiento seguro predeterminado.
El endpoint `/api/csrf` devuelve el token en los mismos campos JSON y el cliente
continúa enviándolo con la misma cookie de sesión y la cabecera indicada.
No debe interpretarse ni reconstruirse el token; debe tratarse como una cadena opaca.
La excepción CSRF de la consola H2 sigue limitada al perfil local `dev` sin `prod`.

### Documentación OpenAPI

La documentación utiliza
`org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`, compatible con Boot 4.
Para conservar el documento OpenAPI 3.0.1 publicado por la API, se configura
`springdoc.api-docs.version=OPENAPI_3_0`.

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

`MascotaRepository` extiende `JpaRepository`, que proporciona operaciones
CRUD y de paginación. Se mantienen las búsquedas
por nombre exacto y por fecha de nacimiento. La carga de los 49 registros de
la fixture de tests, las fechas, los booleanos y los identificadores IDENTITY se
verifican con Hibernate 7 y H2 2.4, usando el esquema Flyway compartido.

El `deleteById` predeterminado del repositorio ignora identificadores inexistentes.
Se ha retirado la implementación artificial que reproducía la excepción antigua.
La existencia se comprueba en `MascotaService`, dentro de su transacción, antes de
usar `repository.delete(mascota)`. La API devuelve 404 si no existe y 204 al eliminarla.

Los cinco mappings GET declaran también la variante terminada en `/`. Las consultas
funcionan con y sin barra final, sin activar opciones globales de compatibilidad
ni ampliar los permisos de escritura.

Se conserva la respuesta de paginación directa y sus metadatos. Spring Data advierte
que serializar PageImpl directamente no garantiza un formato estable entre versiones;
por ello se añaden pruebas de contrato y se compara el JSON real antes y después.
Adoptar otro DTO paginado cambiaría el contrato y queda fuera de esta migración.

Se utilizan PostgreSQL persistente, Flyway y `ddl-auto=validate`. H2 queda para
el perfil test. No se cambian las fechas del modelo a LocalDate. Las pruebas de repositorio fuerzan flush y limpian el contexto
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

