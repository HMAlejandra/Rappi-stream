-- ===================================================
-- init.sql — Inicialización de PostgreSQL
-- Se ejecuta automáticamente al crear el contenedor.
-- ===================================================

-- La base de datos 'rappi_stream_db' ya fue creada por
-- la variable POSTGRES_DB en docker-compose.yml.
-- Este script crea las tablas y datos semilla.

-- Extensión para UUID nativos
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ===================================================
-- TABLA: drivers
-- Repartidores disponibles en el sistema.
-- order_processor usa esta tabla para la estrategia
-- de asignación (RoundRobin / First Available).
-- ===================================================
CREATE TABLE IF NOT EXISTS drivers (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    available   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ===================================================
-- TABLA: orders
-- Estado consolidado de cada pedido.
-- Actualizada por order_processor (INSERT inicial)
-- y dashboard_aggregator (UPDATE de estado).
-- ===================================================
CREATE TABLE IF NOT EXISTS orders (
    id              VARCHAR(36)  PRIMARY KEY,
    customer_name   VARCHAR(150) NOT NULL,
    product         VARCHAR(200) NOT NULL,
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    address         TEXT         NOT NULL,
    driver_id       VARCHAR(36)  REFERENCES drivers(id),
    status          VARCHAR(30)  NOT NULL DEFAULT 'CREADO',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ===================================================
-- TABLA: order_events
-- Log de auditoría de todos los eventos procesados.
-- event_id es UNIQUE para garantizar idempotencia:
-- dashboard_aggregator puede reprocesar desde el
-- inicio sin duplicar registros.
-- ===================================================
CREATE TABLE IF NOT EXISTS order_events (
    id              BIGSERIAL    PRIMARY KEY,
    event_id        VARCHAR(36)  NOT NULL UNIQUE,  -- clave de idempotencia
    order_id        VARCHAR(36)  NOT NULL REFERENCES orders(id),
    event_type      VARCHAR(50)  NOT NULL,
    event_timestamp TIMESTAMPTZ  NOT NULL,
    payload         TEXT         NOT NULL,
    recorded_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Índice para consultas por pedido (historial de un pedido específico)
CREATE INDEX IF NOT EXISTS idx_order_events_order_id
    ON order_events(order_id);

-- Índice para consultas por tipo de evento
CREATE INDEX IF NOT EXISTS idx_order_events_event_type
    ON order_events(event_type);

-- ===================================================
-- DATOS SEMILLA: Repartidores disponibles
-- Usados por la estrategia RoundRobin en order_processor
-- ===================================================
INSERT INTO drivers (id, name, available) VALUES
    ('D-001', 'Carlos Mendoza',   true),
    ('D-002', 'Laura Rodríguez',  true),
    ('D-003', 'Andrés Torres',    true),
    ('D-004', 'Valentina Gómez',  true),
    ('D-005', 'Miguel Herrera',   true)
ON CONFLICT (id) DO NOTHING;
