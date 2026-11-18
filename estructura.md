# HotelSpringBoot — Estructura y Código Completo del Proyecto

> **Stack:** Spring Boot 3.3.4 · Java 17 · PostgreSQL · MapStruct 1.5.5 · Bean Validation · Docker Compose  
> **Patrón:** Dominio rico (no anémico) · Arquitectura en capas · DTOs como `record` · MapStruct en tiempo de compilación · Manejo centralizado de errores

---

## Árbol de directorios

```
HotelSpringBoot/
├── .env
├── .env.example
├── .gitignore
├── .dockerignore
├── docker-compose.yml
│
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   └── src/
│       └── main/
│           ├── java/com/hotel/Hotel/
│           │   ├── HotelApplication.java
│           │   │
│           │   ├── config/
│           │   │   └── CorsConfig.java
│           │   │
│           │   ├── domain/
│           │   │   ├── EstadoHabitacion.java      ← enum
│           │   │   ├── EstadoReserva.java          ← enum
│           │   │   ├── RangoFechas.java            ← value object (@Embeddable record)
│           │   │   ├── Habitacion.java             ← entidad abstracta (@Inheritance JOINED)
│           │   │   ├── HabitacionEstandar.java     ← entidad concreta
│           │   │   ├── SuitePresidencial.java      ← entidad concreta
│           │   │   ├── Cliente.java                ← entidad raíz
│           │   │   └── Reserva.java                ← aggregate root
│           │   │
│           │   ├── dto/
│           │   │   ├── request/
│           │   │   │   ├── CrearClienteRequest.java
│           │   │   │   └── CrearReservaRequest.java
│           │   │   └── response/
│           │   │       ├── ClienteResponse.java
│           │   │       ├── ReservaResponse.java
│           │   │       └── ErrorResponse.java
│           │   │
│           │   ├── mapper/
│           │   │   ├── ClienteMapper.java
│           │   │   └── ReservaMapper.java
│           │   │
│           │   ├── repository/
│           │   │   ├── ClienteRepository.java
│           │   │   ├── HabitacionRepository.java
│           │   │   └── ReservaRepository.java
│           │   │
│           │   ├── service/
│           │   │   ├── ClienteService.java
│           │   │   └── ReservaService.java
│           │   │
│           │   ├── controller/
│           │   │   ├── ClienteController.java
│           │   │   ├── ReservaController.java
│           │   │   └── NodeInfoController.java
│           │   │
│           │   └── exception/
│           │       ├── HotelDomainException.java            ← abstract base
│           │       ├── RecursoNoEncontradoException.java    ← HTTP 404
│           │       ├── ReglaNegocioException.java           ← HTTP 400
│           │       ├── HabitacionNoDisponibleException.java ← HTTP 409
│           │       └── GlobalExceptionHandler.java
│           │
│           └── resources/
│               ├── application.properties
│               ├── application-dev.properties
│               └── application-prod.properties
│
└── frontend/                  ← solo proxy Nginx (no hay app JS)
    ├── Dockerfile
    └── nginx.conf
```

---

## Punto de entrada

```java
// HotelApplication.java
package com.hotel.Hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HotelApplication {
    public static void main(String[] args) {
        SpringApplication.run(HotelApplication.class, args);
    }
}
```

---

## Capa de dominio

> Las reglas de negocio viven en las entidades, no en los servicios.  
> Los servicios **orquestan**, las entidades **deciden**.

### Enums

```java
// EstadoHabitacion.java
package com.hotel.Hotel.domain;

public enum EstadoHabitacion {
    DISPONIBLE, OCUPADA, MANTENIMIENTO
}
```

```java
// EstadoReserva.java
package com.hotel.Hotel.domain;

public enum EstadoReserva {
    PENDIENTE, CONFIRMADA, CANCELADA
}
```

---

### RangoFechas — Value Object (`@Embeddable` + `record`)

```java
// RangoFechas.java
package com.hotel.Hotel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Embeddable
public record RangoFechas(
        @Column(name = "fecha_inicio", nullable = false) LocalDateTime fechaInicio,
        @Column(name = "fecha_fin",    nullable = false) LocalDateTime fechaFin) {

    // Constructor compacto: valida invariantes en construcción
    public RangoFechas {
        if (fechaInicio == null || fechaFin == null)
            throw new IllegalArgumentException("Las fechas no pueden ser nulas");
        if (fechaFin.isBefore(fechaInicio))
            throw new IllegalArgumentException("La fecha fin no puede ser anterior a la de inicio");
    }

    // Comportamiento de dominio: calcula los días del período
    public long getDias() {
        return ChronoUnit.DAYS.between(fechaInicio, fechaFin);
    }
}
```

**Por qué así:** Es un `record` para inmutabilidad automática. El constructor compacto garantiza que nunca exista un `RangoFechas` inválido. `getDias()` es el único comportamiento de dominio y es el que alimenta el cálculo del costo en `Reserva`.

---

### Habitacion — Entidad abstracta con herencia `JOINED`

```java
// Habitacion.java
package com.hotel.Hotel.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "habitaciones")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Habitacion {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    @Column(name = "capacidad_maxima", nullable = false)
    private int capacidadMaxima;

    @Column(name = "precio_por_noche", nullable = false)
    private double precioPorNoche;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoHabitacion estado;

    protected Habitacion() {} // solo para JPA

    public Habitacion(String numero, int capacidadMaxima, double precioPorNoche) {
        if (numero == null || numero.isBlank())
            throw new IllegalArgumentException("Número obligatorio");
        if (capacidadMaxima < 1)
            throw new IllegalArgumentException("Capacidad mínima: 1");
        if (precioPorNoche < 0)
            throw new IllegalArgumentException("Precio no puede ser negativo");

        this.id              = UUID.randomUUID();
        this.numero          = numero;
        this.capacidadMaxima = capacidadMaxima;
        this.precioPorNoche  = precioPorNoche;
        this.estado          = EstadoHabitacion.DISPONIBLE;
    }

    // Máquina de estados de la habitación — llamada por Reserva.confirmar()
    public void asignarAReserva() {
        if (this.estado != EstadoHabitacion.DISPONIBLE)
            throw new IllegalStateException("La habitación no está disponible");
        this.estado = EstadoHabitacion.OCUPADA;
    }

    // Llamada por Reserva.cancelar()
    public void habilitar() {
        this.estado = EstadoHabitacion.DISPONIBLE;
    }

    public UUID getId()                 { return id; }
    public String getNumero()           { return numero; }
    public int getCapacidadMaxima()     { return capacidadMaxima; }
    public double getPrecioPorNoche()   { return precioPorNoche; }
    public EstadoHabitacion getEstado() { return estado; }
}
```

---

### HabitacionEstandar — Subentidad

```java
// HabitacionEstandar.java
package com.hotel.Hotel.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "habitaciones_estandar")
public class HabitacionEstandar extends Habitacion {

    @Column(name = "camas_individuales", nullable = false)
    private int camasIndividuales;

    protected HabitacionEstandar() {}

    public HabitacionEstandar(String numero, int capacidadMaxima,
                               double precioPorNoche, int camasIndividuales) {
        super(numero, capacidadMaxima, precioPorNoche);
        this.camasIndividuales = camasIndividuales;
    }

    public int getCamasIndividuales() { return camasIndividuales; }
}
```

---

### SuitePresidencial — Subentidad

```java
// SuitePresidencial.java
package com.hotel.Hotel.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "suites_presidenciales")
public class SuitePresidencial extends Habitacion {

    @Column(name = "incluye_mayordomo", nullable = false)
    private boolean incluyeMayordomo;

    @Column(name = "jacuzzi_privado", nullable = false)
    private boolean jacuzziPrivado;

    protected SuitePresidencial() {}

    public SuitePresidencial(String numero, int capacidadMaxima, double precioPorNoche,
                              boolean mayordomo, boolean jacuzzi) {
        super(numero, capacidadMaxima, precioPorNoche);
        this.incluyeMayordomo = mayordomo;
        this.jacuzziPrivado   = jacuzzi;
    }

    public boolean isIncluyeMayordomo() { return incluyeMayordomo; }
    public boolean isJacuzziPrivado()   { return jacuzziPrivado; }
}
```

---

### Cliente — Entidad con reglas de penalización

```java
// Cliente.java
package com.hotel.Hotel.domain;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false)
    private boolean activo;

    @Column(nullable = false)
    private int penalizaciones;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reserva> reservas = new ArrayList<>();

    protected Cliente() {}

    public Cliente(String nombre, String email) {
        if (nombre == null || nombre.isBlank())
            throw new IllegalArgumentException("El nombre es obligatorio");
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("El email es obligatorio");

        this.id             = UUID.randomUUID();
        this.nombre         = nombre;
        this.email          = email;
        this.activo         = true;
        this.penalizaciones = 0;
    }

    // Regla de negocio: 3 penalizaciones desactivan al cliente
    public void registrarPenalizacion() {
        this.penalizaciones++;
        if (this.penalizaciones >= 3) {
            this.activo = false;
        }
    }

    public void reactivar() {
        this.activo         = true;
        this.penalizaciones = 0;
    }

    public boolean puedeRealizarReservas() { return this.activo; }

    public UUID getId()            { return id; }
    public String getNombre()      { return nombre; }
    public String getEmail()       { return email; }
    public boolean isActivo()      { return activo; }
    public int getPenalizaciones() { return penalizaciones; }

    // Vista inmutable de la colección interna
    public List<Reserva> getReservas() {
        return Collections.unmodifiableList(reservas);
    }
}
```

**Por qué así:** `registrarPenalizacion()` lleva la regla "3 penalizaciones = inactivo" dentro de la entidad. El servicio la llama pero no decide: solo delega. `getReservas()` retorna una lista no modificable para proteger la colección interna.

---

### Reserva — Aggregate Root

```java
// Reserva.java
package com.hotel.Hotel.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "reservas")
public class Reserva {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habitacion_id", nullable = false)
    private Habitacion habitacion;

    @Embedded
    private RangoFechas periodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoReserva estado;

    @Column(name = "costo_total", nullable = false)
    private double costoTotal;

    protected Reserva() {}

    public Reserva(Cliente cliente, Habitacion habitacion, RangoFechas periodo) {
        if (cliente    == null) throw new IllegalArgumentException("Cliente obligatorio");
        if (habitacion == null) throw new IllegalArgumentException("Habitación obligatoria");
        if (periodo    == null) throw new IllegalArgumentException("Periodo obligatorio");

        this.id         = UUID.randomUUID();
        this.cliente    = cliente;
        this.habitacion = habitacion;
        this.periodo    = periodo;
        this.estado     = EstadoReserva.PENDIENTE;
        // Costo calculado en construcción: mínimo 1 día
        this.costoTotal = habitacion.getPrecioPorNoche() * Math.max(1, periodo.getDias());
    }

    // Coordina la transición de estado: Reserva → CONFIRMADA, Habitacion → OCUPADA
    public void confirmar() {
        this.habitacion.asignarAReserva();
        this.estado = EstadoReserva.CONFIRMADA;
    }

    // Coordina la transición: Reserva → CANCELADA, Habitacion → DISPONIBLE
    public void cancelar() {
        this.habitacion.habilitar();
        this.estado = EstadoReserva.CANCELADA;
    }

    public UUID getId()                { return id; }
    public Cliente getCliente()        { return cliente; }
    public Habitacion getHabitacion()  { return habitacion; }
    public RangoFechas getPeriodo()    { return periodo; }
    public EstadoReserva getEstado()   { return estado; }
    public double getCostoTotal()      { return costoTotal; }
}
```

**Por qué así:** `confirmar()` y `cancelar()` delegan directamente a `Habitacion`, coordinando dos máquinas de estado en un solo método. El costo se calcula una vez, en construcción, usando el value object `RangoFechas`.

---

## DTOs — Request

> Todos son `record` con anotaciones de Bean Validation. Son inmutables por diseño.

### CrearClienteRequest

```java
// CrearClienteRequest.java
package com.hotel.Hotel.dto.request;

import jakarta.validation.constraints.*;

public record CrearClienteRequest(
    @NotBlank(message = "El nombre del cliente no puede estar vacío.")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres.")
    String nombre,

    @NotBlank(message = "El correo electrónico es obligatorio.")
    @Email(message = "El formato del correo electrónico es inválido.")
    String email
) {}
```

### CrearReservaRequest

```java
// CrearReservaRequest.java
package com.hotel.Hotel.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

public record CrearReservaRequest(
    @NotNull(message = "El identificador del cliente es obligatorio.")
    UUID clienteId,

    @NotNull(message = "El identificador de la habitación es obligatorio.")
    UUID habitacionId,

    @NotNull(message = "La fecha de inicio es obligatoria.")
    @FutureOrPresent(message = "La fecha de inicio debe ser hoy o una fecha futura.")
    LocalDateTime fechaInicio,

    @NotNull(message = "La fecha de finalización es obligatoria.")
    @Future(message = "La fecha de finalización debe ser posterior a hoy.")
    LocalDateTime fechaFin
) {}
```

---

## DTOs — Response

> Sin anotaciones de validación. Solo proyectan datos hacia el cliente HTTP.

### ClienteResponse

```java
// ClienteResponse.java
package com.hotel.Hotel.dto.response;

import java.util.UUID;

public record ClienteResponse(
        UUID    id,
        String  nombre,
        String  email,
        boolean activo,
        int     penalizaciones) {}
```

### ReservaResponse

```java
// ReservaResponse.java
package com.hotel.Hotel.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservaResponse(
        UUID          id,
        String        nombreHuesped,    // ← mapeado desde cliente.nombre
        String        habitacionNumero, // ← mapeado desde habitacion.numero
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        double        costoTotal,
        String        estado) {}        // ← enum convertido a String por MapStruct
```

### ErrorResponse

```java
// ErrorResponse.java
package com.hotel.Hotel.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        LocalDateTime       timestamp,
        int                 codigoHttp,
        String              estado,
        String              mensaje,
        String              ruta,
        Map<String, String> detallesValidacion) {    // null en errores simples

    // Para errores de negocio (sin mapa de campos)
    public static ErrorResponse deError(int codigoHttp, String estado,
                                         String mensaje, String ruta) {
        return new ErrorResponse(LocalDateTime.now(), codigoHttp, estado, mensaje, ruta, null);
    }

    // Para fallos de Bean Validation (con mapa campo → mensaje)
    public static ErrorResponse deValidacion(int codigoHttp, String estado, String mensaje,
                                              String ruta, Map<String, String> errores) {
        return new ErrorResponse(LocalDateTime.now(), codigoHttp, estado, mensaje, ruta, errores);
    }
}
```

---

## Mappers (MapStruct)

> Interfaces que MapStruct implementa en tiempo de compilación. Cero código en runtime.

### ClienteMapper

```java
// ClienteMapper.java
package com.hotel.Hotel.mapper;

import com.hotel.Hotel.domain.Cliente;
import com.hotel.Hotel.dto.request.CrearClienteRequest;
import com.hotel.Hotel.dto.response.ClienteResponse;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ClienteMapper {

    ClienteResponse       toResponse(Cliente cliente);
    List<ClienteResponse> toResponseList(List<Cliente> clientes);

    @Mapping(target = "id",             ignore = true)
    @Mapping(target = "activo",         ignore = true)
    @Mapping(target = "penalizaciones", ignore = true)
    @Mapping(target = "reservas",       ignore = true)
    Cliente toEntity(CrearClienteRequest request);
}
```

### ReservaMapper

```java
// ReservaMapper.java
package com.hotel.Hotel.mapper;

import com.hotel.Hotel.domain.Reserva;
import com.hotel.Hotel.dto.response.ReservaResponse;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ReservaMapper {

    // MapStruct resuelve cliente.nombre → nombreHuesped
    //                       habitacion.numero → habitacionNumero por convención de nombres
    @Mapping(target = "estado", expression = "java(reserva.getEstado().name())")
    ReservaResponse       toResponse(Reserva reserva);

    List<ReservaResponse> toResponseList(List<Reserva> reservas);
}
```

---

## Repositorios

> Interfaces que extienden `JpaRepository`. Solo se declaran los métodos que Spring Data no puede derivar por convención.

```java
// ClienteRepository.java
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
    Optional<Cliente> findByEmail(String email);
}
```

```java
// HabitacionRepository.java
@Repository
public interface HabitacionRepository extends JpaRepository<Habitacion, UUID> {
    Optional<Habitacion> findByNumero(String numero);
}
```

```java
// ReservaRepository.java
@Repository
public interface ReservaRepository extends JpaRepository<Reserva, UUID> {
    List<Reserva> findByClienteId(UUID clienteId);
}
```

---

## Servicios

> Los servicios cargan agregados, validan precondiciones de orquestación y delegan el comportamiento al dominio.

### ClienteService

```java
// ClienteService.java
package com.hotel.Hotel.service;

import com.hotel.Hotel.domain.Cliente;
import com.hotel.Hotel.dto.request.CrearClienteRequest;
import com.hotel.Hotel.dto.response.ClienteResponse;
import com.hotel.Hotel.mapper.ClienteMapper;
import com.hotel.Hotel.repository.ClienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ClienteMapper     clienteMapper;

    public ClienteService(ClienteRepository clienteRepository, ClienteMapper clienteMapper) {
        this.clienteRepository = clienteRepository;
        this.clienteMapper     = clienteMapper;
    }

    @Transactional
    public ClienteResponse crear(CrearClienteRequest request) {
        Cliente cliente  = new Cliente(request.nombre(), request.email());
        Cliente guardado = clienteRepository.save(cliente);
        return clienteMapper.toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<ClienteResponse> listarTodos() {
        return clienteMapper.toResponseList(clienteRepository.findAll());
    }

    @Transactional(readOnly = true)
    public ClienteResponse obtenerPorId(UUID id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado con ID: " + id));
        return clienteMapper.toResponse(cliente);
    }
}
```

### ReservaService

```java
// ReservaService.java
package com.hotel.Hotel.service;

import com.hotel.Hotel.domain.*;
import com.hotel.Hotel.dto.request.CrearReservaRequest;
import com.hotel.Hotel.dto.response.ReservaResponse;
import com.hotel.Hotel.exception.*;
import com.hotel.Hotel.mapper.ReservaMapper;
import com.hotel.Hotel.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ReservaService {

    private final ReservaRepository    reservaRepository;
    private final ClienteRepository    clienteRepository;
    private final HabitacionRepository habitacionRepository;
    private final ReservaMapper        reservaMapper;

    public ReservaService(ReservaRepository reservaRepository,
                          ClienteRepository clienteRepository,
                          HabitacionRepository habitacionRepository,
                          ReservaMapper reservaMapper) {
        this.reservaRepository    = reservaRepository;
        this.clienteRepository    = clienteRepository;
        this.habitacionRepository = habitacionRepository;
        this.reservaMapper        = reservaMapper;
    }

    @Transactional
    public ReservaResponse crear(CrearReservaRequest request) {
        if (request.fechaFin().isBefore(request.fechaInicio()))
            throw new ReglaNegocioException("La fecha de salida no puede ser anterior a la fecha de entrada.");

        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", request.clienteId()));

        Habitacion habitacion = habitacionRepository.findById(request.habitacionId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Habitacion", request.habitacionId()));

        if (!cliente.isActivo())
            throw new ReglaNegocioException("El cliente está inactivo o penalizado y no puede crear reservas.");

        RangoFechas periodo      = new RangoFechas(request.fechaInicio(), request.fechaFin());
        Reserva     nuevaReserva = new Reserva(cliente, habitacion, periodo);
        Reserva     guardada     = reservaRepository.save(nuevaReserva);

        return reservaMapper.toResponse(guardada);
    }

    @Transactional
    public ReservaResponse confirmar(UUID reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));
        reserva.confirmar(); // delega al dominio
        return reservaMapper.toResponse(reservaRepository.save(reserva));
    }

    @Transactional
    public ReservaResponse cancelar(UUID reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));
        reserva.cancelar(); // delega al dominio
        return reservaMapper.toResponse(reservaRepository.save(reserva));
    }

    @Transactional(readOnly = true)
    public ReservaResponse obtenerPorId(UUID id) {
        return reservaMapper.toResponse(
            reservaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva", id)));
    }

    @Transactional(readOnly = true)
    public List<ReservaResponse> listarTodas() {
        return reservaMapper.toResponseList(reservaRepository.findAll());
    }
}
```

---

## Controladores

> No contienen lógica. Reciben la petición HTTP, la pasan al servicio, construyen la respuesta.

### ClienteController

```java
// ClienteController.java
package com.hotel.Hotel.controller;

import com.hotel.Hotel.dto.request.CrearClienteRequest;
import com.hotel.Hotel.dto.response.ClienteResponse;
import com.hotel.Hotel.service.ClienteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@RequestBody CrearClienteRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        ClienteResponse response = clienteService.crear(request);
        URI uri = uriBuilder.path("/api/clientes/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ClienteResponse>> listar() {
        return ResponseEntity.ok(clienteService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(clienteService.obtenerPorId(id));
    }
}
```

### ReservaController

```java
// ReservaController.java
package com.hotel.Hotel.controller;

import com.hotel.Hotel.dto.request.CrearReservaRequest;
import com.hotel.Hotel.dto.response.ReservaResponse;
import com.hotel.Hotel.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @PostMapping
    public ResponseEntity<ReservaResponse> crearReserva(
            @Valid @RequestBody CrearReservaRequest request,  // @Valid activa Bean Validation
            UriComponentsBuilder uriBuilder) {
        ReservaResponse creada = reservaService.crear(request);
        URI ruta = uriBuilder.path("/api/reservas/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(ruta).body(creada);
    }

    @GetMapping
    public ResponseEntity<List<ReservaResponse>> listarReservas() {
        return ResponseEntity.ok(reservaService.listarTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponse> buscarPorId(@PathVariable UUID id) {
        // Sin try-catch: si el servicio lanza RecursoNoEncontradoException,
        // GlobalExceptionHandler la captura y devuelve HTTP 404 automáticamente
        return ResponseEntity.ok(reservaService.obtenerPorId(id));
    }
}
```

### NodeInfoController

```java
// NodeInfoController.java — para verificar balanceo de carga entre réplicas Docker
@RestController
@RequestMapping("/api/node")
public class NodeInfoController {

    @GetMapping("/whoami")
    public Map<String, String> whoAmI() {
        String containerId;
        try {
            containerId = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            containerId = "unknown-node";
        }
        return Map.of(
            "status",         "UP",
            "served_by_node", containerId,
            "runtime",        "Spring Boot Grid Replica");
    }
}
```

---

## Excepciones

### Jerarquía

```
RuntimeException
└── HotelDomainException  (abstract)
    ├── RecursoNoEncontradoException    → HTTP 404
    ├── ReglaNegocioException           → HTTP 400
    └── HabitacionNoDisponibleException → HTTP 409
```

```java
// HotelDomainException.java
public abstract class HotelDomainException extends RuntimeException {
    protected HotelDomainException(String mensaje) {
        super(mensaje);
    }
}
```

```java
// RecursoNoEncontradoException.java
public class RecursoNoEncontradoException extends HotelDomainException {
    public RecursoNoEncontradoException(String recurso, UUID id) {
        super(String.format("%s con identificador %s no fue encontrado en el sistema.", recurso, id));
    }
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
```

```java
// ReglaNegocioException.java
public class ReglaNegocioException extends HotelDomainException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
```

```java
// HabitacionNoDisponibleException.java
public class HabitacionNoDisponibleException extends HotelDomainException {
    public HabitacionNoDisponibleException(String numeroHabitacion) {
        super(String.format("La habitación %s no está disponible en las fechas seleccionadas.",
              numeroHabitacion));
    }
}
```

### GlobalExceptionHandler

```java
// GlobalExceptionHandler.java
package com.hotel.Hotel.exception;

import com.hotel.Hotel.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarRecursoNoEncontrado(
            RecursoNoEncontradoException ex, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.deError(
                HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> manejarReglaNegocio(
            ReglaNegocioException ex, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.deError(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HabitacionNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> manejarConflictoHabitacion(
            HabitacionNoDisponibleException ex, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.deError(
                HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacionesDto(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> erroresPorCampo = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            erroresPorCampo.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponse error = ErrorResponse.deValidacion(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Error en la validación de los datos enviados.",
                request.getRequestURI(), erroresPorCampo);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Red de seguridad — oculta detalles internos en producción
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorGenerico(
            Exception ex, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.deError(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Ocurrió un error interno no controlado. Comuníquese con soporte.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

---

## Configuración

### CorsConfig

```java
// CorsConfig.java
package com.hotel.Hotel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins; // inyectado desde el perfil activo

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With")
                .exposedHeaders("Location")
                .allowCredentials(true)
                .maxAge(3600); // caché preflight: 1 hora
    }
}
```

### application.properties (base)

```properties
spring.application.name=hotel-reservas-app
spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}

spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/hotel_reservas_db}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:postgres}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:postgres}
spring.datasource.driver-class-name=org.postgresql.Driver
```

### application-dev.properties

```properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
app.cors.allowed-origins=http://localhost:4200,http://localhost:3000,http://localhost:8080
```

### application-prod.properties

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
server.error.include-stacktrace=never
server.error.include-message=never
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost}
```

---

## Infraestructura Docker

### backend/Dockerfile (multi-stage)

```dockerfile
# Etapa 1: compilación
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B          # cachea dependencias
COPY src ./src
RUN mvn package -DskipTests -B

# Etapa 2: runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser                               # no-root por seguridad
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

### docker-compose.yml (estructura)

```yaml
services:
  postgres-db:        # PostgreSQL 15, volumen persistente, healthcheck
  backend-api:        # 2 réplicas, espera DB sana, variables de entorno
  web-gateway:        # Nginx — proxy hacia las réplicas de backend

networks:
  backend-net:        # DB ↔ backend (aislada del exterior)
  frontend-net:       # Nginx ↔ backend (sin acceso a DB)
```

---

## Patrones de diseño aplicados

| Patrón | Dónde se aplica |
|---|---|
| **Dominio rico** | `Cliente.registrarPenalizacion()`, `Reserva.confirmar()`, `Habitacion.asignarAReserva()` |
| **Value Object** | `RangoFechas` como `@Embeddable record` auto-validado |
| **Herencia con tabla por subclase** | `Habitacion` → `HabitacionEstandar` / `SuitePresidencial` (`JOINED`) |
| **DTO de entrada / salida** | `record` con Bean Validation en request; `record` plano en response |
| **Mapper en compilación** | MapStruct — cero reflexión en runtime |
| **Excepción tipada → HTTP** | Jerarquía `HotelDomainException` capturada por `@RestControllerAdvice` |
| **Constructor-only identity** | UUID generado en el constructor del dominio, `updatable = false` en JPA |
| **Inyección por constructor** | Todos los servicios y controladores, sin `@Autowired` en campo |
| **Perfil de configuración** | `dev` vs `prod` con DDL, SQL logging y CORS distintos |
| **Contenedor sin root** | `adduser appuser` en Dockerfile; `USER appuser` antes del `ENTRYPOINT` |
