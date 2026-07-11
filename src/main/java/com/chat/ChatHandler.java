package com.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
/**
 * ChatHandler — gestiona el ciclo de vida de cada sesión WebSocket.
 * CopyOnWriteArraySet es thread-safe: Spring puede llamar a estos
 * métodos desde distintos hilos simultáneamente.
 */
@Component
public class ChatHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatHandler.class);

    // Conjunto de todas las sesiones WebSocket activas
    private final Set<WebSocketSession> sessions =
            new CopyOnWriteArraySet<>();

    private final ObjectMapper mapper = new ObjectMapper();

    private final Counter messagesReceived;
    private final Counter messagesBroadcast;
    private final Tracer tracer;

    public ChatHandler(MeterRegistry registry, Tracer tracer) {
        this.tracer = tracer;
        // Counter: acumula el total de mensajes recibidos (solo sube)
        this.messagesReceived = Counter.builder("ws.messages.received")
                .description("Total de mensajes recibidos del cliente")
                .register(registry);

        // Counter: mensajes enviados en broadcast (puede ser N por mensaje)
        this.messagesBroadcast = Counter.builder("ws.messages.broadcast")
                .description("Total de mensajes enviados a clientes")
                .register(registry);

        // Gauge: valor actual de sesiones abiertas (sube y baja)
        Gauge.builder("ws.sessions.active", sessions, Set::size)
                .description("Sesiones WebSocket activas en este momento")
                .register(registry);
    }

    // ── Se llama cuando un cliente se CONECTA ──────────
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WS_CONNECTED",
                StructuredArguments.kv("session_id", session.getId()),
                StructuredArguments.kv("active_sessions", sessions.size())
        );

        ChatMessage msg = new ChatMessage(
                "system", "Un usuario se conectó", sessions.size());
        broadcast(msg);
    }

    // ── Se llama cuando llega un MENSAJE de texto ──────
    @Override
    protected void handleTextMessage(WebSocketSession session,
                                      TextMessage message) throws Exception {
        Span span = tracer.nextSpan()
                .name("ws.handle-message")
                .tag("session.id", session.getId())
                .start();

        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            messagesReceived.increment();

            // Deserializar el JSON que mandó React
            ChatMessage incoming = mapper.readValue(
                    message.getPayload(), ChatMessage.class);

            log.info("WS_MESSAGE",
                    StructuredArguments.kv("username", incoming.getUsername()),
                    StructuredArguments.kv("message_length", incoming.getText().length()),
                    StructuredArguments.kv("active_sessions", sessions.size())
            );

            // Agregar contexto al span — se verá en la UI de Zipkin
            span.tag("chat.username", incoming.getUsername());
            span.tag("chat.text_length", String.valueOf(incoming.getText().length()));
            span.tag("chat.active_users", String.valueOf(sessions.size()));

            // Construir la respuesta con timestamp del servidor
            ChatMessage outgoing = new ChatMessage();
            outgoing.setType("message");
            outgoing.setUsername(incoming.getUsername());
            outgoing.setText(incoming.getText());
            outgoing.setTimestamp(Instant.now().toString());

            broadcast(outgoing);

        } catch (Exception e) {
            span.error(e); // ← marca el span como FAILED en Zipkin
            System.err.println("Mensaje inválido: " + e.getMessage());
        } finally {
            span.end(); // ← SIEMPRE cerrar el span, incluso si hay error
        }
    }

    // ── Se llama cuando un cliente se DESCONECTA ───────
    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("WS_DISCONNECTED",
                StructuredArguments.kv("session_id", session.getId()),
                StructuredArguments.kv("close_status", status.getCode()),
                StructuredArguments.kv("active_sessions", sessions.size())
        );

        ChatMessage msg = new ChatMessage(
                "system", "Un usuario se desconectó", sessions.size());
        broadcast(msg);
    }

    // ── Broadcast: envía a TODAS las sesiones abiertas ─
    private void broadcast(ChatMessage msg) throws Exception {
        String json = mapper.writeValueAsString(msg);
        TextMessage frame = new TextMessage(json);

        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                synchronized (s) {  // WebSocketSession NO es thread-safe al escribir
                    s.sendMessage(frame);
                }
                messagesBroadcast.increment();
            }
        }
    }
}
