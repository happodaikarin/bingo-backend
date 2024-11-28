package com.granbuda.bingo.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameSession {
    private String sessionId;
    private List<String> players = new ArrayList<>(); // Lista de jugadores inicializada
    private List<Integer> drawnBalls = new ArrayList<>(); // Balotas sorteadas inicializada
    private boolean isGameOver = false; // Estado inicial del juego
    private Map<String, List<List<String>>> bingoCards; // Tarjetones de los jugadores
}
