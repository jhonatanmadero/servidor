# ws-server

Servidor WebSocket para un chat en vivo, construido con Spring Boot puro (sin STOMP/SockJS).

## Stack

- Java 17
- Spring Boot 3.3.5
- `spring-boot-starter-websocket`
- Jackson (serialización JSON)
- Maven

## Funcionalidad

- Maneja conexiones WebSocket en `ws://localhost:8080/chat`.
- Reenvía (broadcast) cada mensaje recibido a todos los clientes conectados.
- Notifica cuando un usuario se conecta o desconecta, incluyendo el total de sesiones activas.
- Soporta señales de "usuario escribiendo..." además de los mensajes de chat.

## Requisitos

- Java 17 o superior
- Maven 3.8 o superior

## Cómo correrlo

```bash
mvn spring-boot:run
```

El servidor queda escuchando en el puerto **8080**, con el endpoint WebSocket en:

```
ws://localhost:8080/chat
```

## Estructura

```
src/main/java/com/chat/
├── WsServerApplication.java   # Clase principal
├── ChatMessage.java           # DTO del mensaje (JSON)
├── ChatHandler.java           # Lógica de conexión/mensajes/broadcast
└── WebSocketConfig.java       # Registro del endpoint y CORS
src/main/resources/
└── application.properties
```

## Configuración

Por defecto solo acepta conexiones desde `http://localhost:5173` (el cliente React en desarrollo). Para producción, cambia el origen permitido en `WebSocketConfig.java`:

## Proyecto relacionado

El cliente (React + Vite) que consume este servidor vive en un repositorio separado: `client/`.