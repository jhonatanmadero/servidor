package com.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    // Conjunto de todas las sesiones WebSocket activas
    private final Set<WebSocketSession> sessions =
            new CopyOnWriteArraySet<>();

    private final ObjectMapper mapper = new ObjectMapper();

    // ── Se llama cuando un cliente se CONECTA ──────────
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("Conectado: " + session.getId()
                + " | Total: " + sessions.size());

        ChatMessage msg = new ChatMessage(
                "system", "Un usuario se conectó", sessions.size());
        broadcast(msg);
    }

    // ── Se llama cuando llega un MENSAJE de texto ──────
    @Override
    protected void handleTextMessage(WebSocketSession session,
                                      TextMessage message) throws Exception {
        try {
            // Deserializar el JSON que mandó React
            ChatMessage incoming = mapper.readValue(
                    message.getPayload(), ChatMessage.class);

            System.out.println("[" + incoming.getUsername() + "] "
                    + incoming.getText());

            // Construir la respuesta con timestamp del servidor
            ChatMessage outgoing = new ChatMessage();
            outgoing.setType("message");
            outgoing.setUsername(incoming.getUsername());
            outgoing.setText(incoming.getText());
            outgoing.setTimestamp(Instant.now().toString());

            broadcast(outgoing);

        } catch (Exception e) {
            System.err.println("Mensaje inválido: " + e.getMessage());
        }
    }

    // ── Se llama cuando un cliente se DESCONECTA ───────
    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                       CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("Desconectado: " + session.getId()
                + " | Total: " + sessions.size());

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
            }
        }
    }
}
