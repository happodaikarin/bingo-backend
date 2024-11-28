package com.granbuda.bingo.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {
    private String type; // Tipo de mensaje (e.g., "ASSIGN_CARD", "NEW_BALL")
    private String player; // Nombre del jugador (opcional)
    private List<String> players; // Lista de jugadores (opcional)
    private String data; // Datos adicionales (opcional)

    // Constructor para mensajes con tipo y jugador
    public GameMessage(String type, String player) {
        this.type = type;
        this.player = player;
    }

    // Constructor para mensajes con tipo, jugador y datos adicionales
    public GameMessage(String type, String player, String data) {
        this.type = type;
        this.player = player;
        this.data = data;
    }
}
