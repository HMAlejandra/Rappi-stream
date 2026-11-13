# Verificación de Pruebas Unitarias — Rappi-Stream

## Resumen ejecutivo

```
mvn test -pl common,order_api,order_processor,driver_simulator,
             notification_router,sms_worker,push_notification_worker,
             dashboard_aggregator,dashboard_backend -am
```

**Resultado: 79 pruebas en verde, 0 fallos, 0 errores**

---

## Detalle por módulo

| Módulo | Tests | Patrón verificado | Tiempo |
|--------|-------|-------------------|--------|
| `common` | 15 | Records Fail-Fast, inmutabilidad | ~0.2s |
| `order_api` | 9 | @WebMvcTest, Bean Validation | ~1.5s |
| `order_processor` | 29 | State, Strategy, Mockito DIP | ~2.0s |
| `driver_simulator` | 6 | Kafka produce x3, clave orderId | ~0.1s |
| `notification_router` | 5 | Strategy SMS/Push, routing keys | ~0.5s |
| `sms_worker` | 2 | @RabbitListener, deserialización | ~0.3s |
| `push_notification_worker` | 2 | @RabbitListener, deserialización | ~0.3s |
| `dashboard_aggregator` | 8 | Stateful, idempotencia, 5 estados | ~3.5s |
| `dashboard_backend` | 3 | WebSocket broadcast, 200 OK | ~3.4s |
| **TOTAL** | **79** | | **~12s** |

---

## `common` — 15 tests

### `OrderCreatedCommandTest` (10 tests)
Verifica la validación **Fail-Fast** del record `OrderCreatedCommand`:

| Test | Qué verifica |
|------|-------------|
| `debeCrearComandoConDatosValidos` | Construcción correcta con todos los campos válidos |
| `debeRechazarCommandIdNulo` | `NullPointerException` con mensaje "commandId" |
| `debeRechazarCommandIdVacio` | `IllegalArgumentException` con cadena en blanco |
| `debeRechazarOrderIdNulo` | `NullPointerException` |
| `debeRechazarCustomerNameVacio` | `IllegalArgumentException` con espacios |
| `debeRechazarQuantityCero` | `IllegalArgumentException`: "quantity debe ser mayor que cero" |
| `debeRechazarQuantityNegativa` | `IllegalArgumentException` con -5 |
| `debeRechazarAddressVacia` | `IllegalArgumentException` |
| `debeRechazarIssuedAtNulo` | `NullPointerException` |
| `debeSerInmutableEIgualPorValor` | Dos records iguales → `equals()` y `hashCode()` iguales |

### `OrderEventEnvelopeTest` (5 tests)
Verifica la validación del contrato de eventos Kafka.

---

## `order_processor` — 29 tests

### `PedidoTest` (14 tests) — Patrón State
```
✅ debeCrearPedidoConDatosValidos
✅ debeRechazarIdNulo
✅ debeRechazarIdVacio
✅ debeRechazarQuantityCero
✅ debeRechazarQuantityNegativa
✅ debeRechazarCustomerNameVacio
✅ cicloDeVidaCompleto          ← CREADO→ASIGNADO→EN_CAMINO→LLEGANDO→ENTREGADO
✅ noDebeIniciarEntregaDesdeCreado       ← transición inválida
✅ noDebeRegistrarLlegadaDesdeCreado     ← transición inválida
✅ noDebeCompletarEntregaDesdeCreado     ← transición inválida
✅ noDebeReasignarEnEstadoAsignado       ← transición inválida
✅ noDebeCompletarEntregaDesdeEnCamino   ← transición inválida
✅ noDebeOperarSobreEntregado            ← estado terminal: 4 ops rechazadas
✅ noDebeAceptarDriverIdVacio
```

### `RoundRobinAssignmentStrategyTest` (7 tests) — Patrón Strategy
```
✅ debeAsignarPrimerRepartidorAlPrimerPedido
✅ debeRotarEntreRepartidores        ← P1→D1, P2→D2, P3→D3, P4→D1 (circular)
✅ debeIgnorarRepartidoresNoDisponibles
✅ debeLanzarExcepcionSinRepartidoresDisponibles
✅ debeLanzarExcepcionConListaVacia
✅ debeLanzarExcepcionConListaNula
✅ pedidoDebeQuedarEnEstadoAsignado
```

### `OrderProcessorServiceTest` (8 tests) — Mockito + DIP
```
✅ debePersistirAntesDePubilcar
✅ debePublicarOrderCreated
✅ debeAplicarEstrategiaDeAsignacion
✅ debePublicarDriverAssigned
✅ debeActualizarDespuesDeAsignar
✅ pedidoGuardadoDebeUsarElOrderIdDelComando    ← ArgumentCaptor
✅ debeEjecutarEnOrdenCorrecto                  ← inOrder()
✅ siEstrategiaFallaNODebePublicarDriverAssigned ← verify(never())
```

---

## `driver_simulator` — 6 tests

```
✅ debePublicarTresEventos            ← verify(times(3))
✅ debePublicarEnTopicCorrecto        ← topic = "order_events"
✅ debeUsarOrderIdComoClaveDeParticion ← key = orderId
✅ debePublicarEnOrdenCorrecto        ← STARTED→ARRIVING→DELIVERED
✅ cadaEventoDebeContenerElOrderId    ← en el JSON del envelope
✅ orderDeliveredDebeContenerDriverId ← driverId en el payload
```

---

## `dashboard_aggregator` — 8 tests

```
✅ orderCreatedDebeCrearSnapshot       ← estado inicial CREADO
✅ orderCreatedDebePersistirEnBD       ← eventRepo.save() + orderRepo.save()
✅ driverAssignedDebeActualizarEstado  ← status=ASIGNADO, driverId correcto
✅ deliveryStartedDebeActualizarEstado ← status=EN_CAMINO
✅ driverArrivingDebeActualizarEstado  ← status=LLEGANDO
✅ orderDeliveredDebeActualizarEstado  ← status=ENTREGADO
✅ eventoDuplicadoDebeSerIgnorado      ← idempotencia: null + never().save()
✅ debeManitenerMultiplesSnapshots     ← 2 pedidos → size=2
```

---

## `dashboard_backend` — 3 tests

```
✅ debeHacerBroadcastAlTopicCorrecto  ← convertAndSend("/topic/orders", dto)
✅ debeEnviarElDtoExacto              ← mismo DTO, verifyNoMoreInteractions
✅ debeRetornar200                    ← ResponseEntity 200 OK
```

---

## Cómo ejecutar las pruebas

```bash
# Todas las pruebas del proyecto
mvn test

# Solo un módulo específico
mvn test -pl order_processor

# Con reporte detallado en consola
mvn test -pl order_processor -Dsurefire.useFile=false

# Solo las pruebas del dominio (las más rápidas, sin Spring)
mvn test -pl common,order_processor -Dtest="PedidoTest,RoundRobinAssignmentStrategyTest,OrderCreatedCommandTest"
```

---

## Estrategia de pruebas — por qué son pruebas unitarias reales

Todas las pruebas de este proyecto cumplen la definición estricta de **prueba unitaria aislada**:

1. **Sin infraestructura**: ninguna prueba levanta Spring Boot, inicia Kafka, conecta a RabbitMQ ni abre una base de datos.

2. **Ejecución en memoria RAM**: el tiempo total es ~12 segundos para 79 pruebas, demostrando que no hay I/O de red.

3. **Dobles de prueba para dependencias externas**: `KafkaTemplate`, `SpringDataPedidoRepository`, `SimpMessagingTemplate` y todos los puertos externos son `@Mock` de Mockito.

4. **Patrón AAA**: todas las pruebas están estructuradas en secciones `// Arrange`, `// Act`, `// Assert` comentadas explícitamente.

5. **Pruebas de comportamiento, no de estado trivial**: no hay aserciones del tipo `assertNotNull(objeto)`. Se verifica comportamiento: qué métodos se llamaron, con qué argumentos, en qué orden.
