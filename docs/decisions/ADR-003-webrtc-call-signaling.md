# ADR-003: Señalización de videollamadas WebRTC por sondeo en memoria

## Status
Accepted

## Date
2026-11-19

## Context

El chat y la presencia funcionan por sondeo HTTP (`ChatService` cada 4 s, `PresenceService` con heartbeat), y el backend no tiene ningún canal tiempo real: no hay WebSocket, STOMP ni SSE en el proyecto. El frontend se despliega en Vercel y llega a la API a través del rewrite `/api/:path*`, de modo que cualquier transporte nuevo pasaría por ese proxy.

Las videollamadas WebRTC necesitan un canal de señalización para intercambiar SDP (oferta/respuesta) y candidatos ICE antes de que el audio/vídeo viaje P2P. Ese canal es efímero: no hay historial de llamadas que persistir, y el estado solo vive mientras la llamada está en curso.

## Decision

Las llamadas son **solo entre dos usuarios de un DM** y el estado vive **en memoria** en `CallService` (sin tabla en Flyway). La señalización viaja por el **mismo patrón de sondeo del resto de la app**, con una cola de eventos con cursor `seq`:

- `POST /api/calls` `{channelId}` → crea la sesión en `RINGING` (409 si el canal o alguno de los dos ya tiene una llamada activa).
- `GET /api/calls/active` → llamadas en las que participo y que no han terminado (descubrimiento de entrantes).
- `GET /api/calls/{id}/events?after=<seq>` → eventos posteriores al cursor: `OFFER`, `ANSWER`, `ICE` (con la señal en `payload`, sin interpretar) y `STATE` (transiciones con `state` y `reason`).
- `POST /api/calls/{id}/signal` → encola una señal para la otra parte.
- `POST /api/calls/{id}/answer | decline | end` → transiciones de estado.

La máquina de estados es `RINGING → ACTIVE → ENDED`. El tiempo de sonido es de 60 s (expira a `MISSED` por expiración perezosa, sin programador de tareas) y las llamadas finalizadas se conservan 600 s para que ambos lados lean el desenlace, después se purgan. Los métodos están sincronizados: es una instancia única en Render.

El cliente conserva su propio último `seq` por llamada, **iniciado en 0** al descubrirla (nunca partir del `seq` de la instantánea, o se perdería el OFFER). Un 404 en los eventos significa que la sesión ya no existe: llamada finalizada o reinicio del proceso.

## Consequences

- Un reinicio o deploy del backend corta todas las llamadas en curso; el cliente debe tratar el 404 como "llamada finalizada" y colgar.
- El estado no sobrevive a más de una instancia: si Render escala a réplicas, el sondeo debe fijarse por sesión (sticky) o el diseño debe migrar a un almacén compartido.
- No hay historial de llamadas (no hay tabla); si se quiere "llamadas perdidas" persistentes habrá que añadir migración Flyway.
- La latencia de la señalización es de ~1 s por evento (sondeo), irrelevante para el ringing; el media no sufre ningún retardo porque va P2P.
- Sin TURN, usuarios tras NAT simétrico no conectarán: Open Relay o un coturn propio es un paso posterior.
- Las pruebas (`CallServiceTest`, `CallControllerTest`) se ejecutan solo en CI: este equipo no tiene Java ni Maven.

## Alternatives consideradas

- **WebSocket STOMP (`spring-boot-starter-websocket`)**: rechazado por ahora — dependencia y configuración nuevas, y el proxy de Vercel puede no transportar WebSockets en el plan Hobby, lo que rompería la llamada en producción sin error claro en local.
- **SSE (`SseEmitter`)**: rechazado — mejor latencia, pero los buffers de los proxies intermedios pueden retener el stream y el código de reconexión de `EventSource` complica el cursor de eventos.
- **Señalización en base de datos**: rechazada — el estado es efímero, no aporta nada persistir ofertas caducas y añade una tabla que caduca a los 30 días en Render Free.
