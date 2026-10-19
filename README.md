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
registro de usuarios, JWT ni jerarquías de roles. Las versiones de Spring Boot y
las dependencias existentes se mantienen; las nuevas dependencias de seguridad
utilizan las versiones gestionadas por Spring Boot 2.4.5.

| Recurso | Acceso |
| --- | --- |
| Los cinco endpoints GET de `/api/mascotas` | Público |
| `POST /api/mascotas` | Autenticación y token CSRF |
| `DELETE /api/mascotas/delete/{id}` | Autenticación y token CSRF |
| `GET /api/csrf` | Autenticación; devuelve el token CSRF |
| Swagger UI y `/v2/api-docs` | Autenticación |
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
.\mvnw.cmd -DskipTests package
```

Las pruebas generan credenciales efímeras y no necesitan secretos reales. Comprueban
consultas públicas, rechazos 401/403, altas y borrados autenticados con un token real,
restricciones de H2 y fallos de arranque ante una configuración insegura. Las pruebas
HTTP utilizan MockMvc con los filtros reales de seguridad y la base H2 de pruebas.

## Métodos del MascotaController

El `MascotaController` es un controlador REST en una aplicación Spring Boot que gestiona las operaciones relacionadas con las mascotas. Este controlador proporciona varias operaciones a través de diferentes endpoints:

- **`GET /api/mascotas/{id}`**: Este método devuelve una mascota específica por su ID. Si la mascota existe, se retorna con un estado HTTP 200 (OK), de lo contrario, se retorna un estado HTTP 404 (NOT FOUND).

- **`GET /api/mascotas/nombre/{name}`**: Este método permite buscar mascotas por su nombre. Si no se encuentran mascotas con el nombre especificado, se retorna un estado HTTP 204 (NO CONTENT). Si se encuentran, se retorna una lista de mascotas con un estado HTTP 200 (OK).

- **`POST /api/mascotas`**: Este método permite agregar una nueva mascota a la base de datos. La información de la mascota se recibe en formato JSON en el cuerpo de la solicitud. Si la operación es exitosa, se retorna la mascota creada con un estado HTTP 201 (CREATED).

- **`GET /api/mascotas`**: Este método devuelve todas las mascotas registradas en la base de datos.

- **`GET /api/mascotas/paged20`**: Este método devuelve las 20 mascotas con edad más joven registradas en la base de datos.

- **`GET /api/mascotas/paged/{pag}`**: Este método permite paginar los resultados, devolviendo un conjunto de mascotas basado en el número de página especificado en la URL.

- **`DELETE /api/mascotas/delete/{id}`**: Este método permite eliminar una mascota específica por su ID. Si la operación es exitosa, se retorna un estado HTTP 204 (NO CONTENT).

