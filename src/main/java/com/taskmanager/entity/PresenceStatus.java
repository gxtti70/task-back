package com.taskmanager.entity;

/**
 * Estados de presencia de usuario (estilo Microsoft Teams).
 *
 * Los estados AVAILABLE..APPEAR_OFFLINE pueden establecerse manualmente por el usuario.
 * OFFLINE es un estado derivado: se calcula en el servicio de ausencia por inactividad
 * o falta de heartbeat y nunca se acepta como entrada del cliente.
 */
public enum PresenceStatus {
    AVAILABLE,        // Disponible
    BUSY,             // Ocupado
    DO_NOT_DISTURB,   // No molestar
    BE_RIGHT_BACK,    // Vuelvo enseguida
    AWAY,             // Ausente
    OUT_OF_OFFICE,    // Fuera de la oficina
    APPEAR_OFFLINE,   // Parecer desconectado
    OFFLINE           // Desconectado (derivado, no settable por el cliente)
}
