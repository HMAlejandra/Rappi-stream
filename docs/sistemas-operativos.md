# Sistemas Operativos — Rappi-Stream

## cgroups y Docker

### Qué son los cgroups

Los **cgroups** (control groups) son una característica del kernel Linux que permite agrupar procesos y aplicarles límites de recursos: CPU, memoria, I/O, número de procesos, etc. Un cgroup aísla un conjunto de procesos del resto del sistema para que no puedan consumir más recursos de los asignados.

### Cómo Docker los usa

Docker crea un cgroup por contenedor. Cuando en `docker-compose.yml` definimos:

```yaml
deploy:
  resources:
    limits:
      cpus: '0.50'
      memory: 384M
```

Docker traduce esto en cuotas de cgroup:
- **`cpus: '0.50'`** — el contenedor puede usar como máximo el 50% de un núcleo de CPU. Si el host tiene 4 núcleos, el contenedor no puede usar más de 0.5 núcleos de CPU en total, independientemente de cuántos hilos lance la JVM.
- **`memory: 384M`** — el contenedor tiene un límite duro de 384 MB de RAM. Si la JVM intenta alojar más memoria de la permitida, el kernel Linux mata el proceso con OOM Killer (Out Of Memory Killer).

### Verificación

```bash
docker stats --no-stream
```

Muestra el uso real de CPU y RAM de cada contenedor comparado con sus límites. Si un contenedor muestra `MEM USAGE: 120.5MiB / 384MiB`, el límite está funcionando.

## `-XX:+UseContainerSupport` — la JVM en contenedores

### El problema

Antes de Java 10, la JVM no era consciente de contenedores. Cuando se ejecutaba dentro de un contenedor Docker con un límite de 384 MB, la JVM leía la memoria total del host (por ejemplo, 16 GB) y configuraba su heap basándose en esos 16 GB. Esto causaba que la JVM intentara alojar mucha más memoria de la que el cgroup permitía, provocando que el kernel la matara con OOM Killer.

### Qué hace `-XX:+UseContainerSupport`

Esta flag (activada por defecto desde Java 10+) hace que la JVM lea los límites del cgroup en lugar de los del host:
- Detecta el límite de memoria del cgroup y ajusta el heap máximo.
- Detecta el límite de CPU y ajusta el número de hilos del pool de GC.

Sin esta flag, la JVM vería 16 GB de RAM (del host) en lugar de 384 MB (del contenedor), y el OOM Killer terminaría el proceso.

### Configuración en el proyecto

Todos los microservicios definen:

```yaml
JAVA_OPTS: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
```

## `-XX:MaxRAMPercentage=75.0`

### Qué hace exactamente

Esta flag define qué porcentaje del límite de memoria del contenedor se asigna al heap de la JVM. Con `MaxRAMPercentage=75.0` y un contenedor de 384 MB:

- **Heap máximo:** 384 MB × 0.75 = 288 MB para el heap de objetos Java.
- **Resto (96 MB):** para metaspace, stacks de hilos, código JIT, buffers directos, memoria nativa de librerías (Netty, etc.).

### Por qué 75% y no 100%

La JVM necesita memoria fuera del heap:
- **Metaspace:** almacenamiento de metadatos de clases (~64 MB).
- **Thread stacks:** cada hilo tiene un stack de ~1 MB. Con 50 hilos = 50 MB.
- **JIT code cache:** código compilado en tiempo de ejecución.
- **Direct buffers:** usados por Netty/Kafka clients.

Si se asignara 100% al heap, no quedaría memoria para estos componentes y el proceso moriría por OOM.

### `-XX:+ExitOnOutOfMemoryError`

Esta flag hace que la JVM termine inmediatamente con un código de salida no cero cuando ocurre un `OutOfMemoryError`. Sin ella, la JVM intentaría continuar en un estado degradado, lo que podría causar comportamientos impredecibles. Con `ExitOnOutOfMemoryError`, el contenedor termina limpiamente y Docker lo reinicia (`restart: unless-stopped`).

## Usuario no-root en los Dockerfiles

### Principio de mínimo privilegio

Cada Dockerfile en el proyecto crea un usuario sin privilegios:

```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```

### Por qué importa

Si un atacante logra ejecución de código dentro del contenedor (por ejemplo, vía una vulnerabilidad en una librería), y el proceso corre como root, el atacante tiene acceso root dentro del contenedor. Esto puede escalar a:
- Modificar archivos del sistema dentro del contenedor.
- Intentar escapar del contenedor (container escape).
- Acceder a dispositivos del host montados.

Con un usuario no-root (`appuser`), el atacante solo tiene los permisos de ese usuario, que son mínimos. No puede modificar archivos del sistema, no puede instalar paquetes, no puede cambiar configuración de red.

### Verificación

```bash
docker exec rappi_order_api whoami
# Salida esperada: appuser (no root)
```

## Build multi-etapa en Docker

### Qué es

El build multi-etapa usa múltiples instrucciones `FROM` en un solo Dockerfile. Cada `FROM` inicia una etapa con su propia imagen base. Los artefactos de una etapa se pueden copiar a la siguiente con `COPY --from=builder`.

### Estructura en el proyecto

```dockerfile
# Etapa 1: Build — incluye Maven y JDK completo
FROM maven:3.9.6-eclipse-temurin-17 AS builder
# ... compila el JAR

# Etapa 2: Runtime — solo JRE, sin Maven ni JDK
FROM eclipse-temurin:17-jre-alpine
# ... copia solo el JAR compilado
```

### Por qué la imagen de runtime no incluye Maven ni el JDK

- **Maven (~100 MB)** solo se necesita durante el build. En runtime no se compila nada.
- **JDK completo (~300 MB)** incluye el compilador `javac`, herramientas de debugging, etc. En runtime solo se necesita el JRE (~80 MB) que contiene la JVM.
- **Imagen final más pequeña:** la imagen de runtime es ~120 MB (JRE Alpine + JAR) vs ~500 MB si incluyera Maven + JDK. Imágenes más pequeñas se descargan y despliegan más rápido.

### Reducción de superficie de ataque

La imagen de runtime no contiene herramientas de compilación ni utilidades del JDK. Un atacante que entre al contenedor no tiene acceso a `javac`, `jshell`, `jstack`, etc. Menos herramientas disponibles = menos vectores de ataque.

### Caching de capas

Las capas de descarga de dependencias (`mvn dependency:go-offline`) se separan de las capas de compilación. Si solo cambia el código fuente, Docker reutiliza la capa de dependencias cacheada, acelerando el build.
