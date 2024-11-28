package com.granbuda.bingo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.granbuda.bingo.model.GameMessage;
import com.granbuda.bingo.model.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio que maneja la lógica del juego de Bingo, incluyendo la gestión de jugadores,
 * sesiones, validaciones de Bingo, sorteos de balotas y comunicación con los clientes vía WebSocket.
 */
@Service
public class GameService {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    private final SimpMessagingTemplate messagingTemplate;

    // Mapa para manejar sesiones activas de juego
    private final Map<String, GameSession> activeSessions = new ConcurrentHashMap<>();

    // Mapa para almacenar los tarjetones de bingo de los jugadores
    private final Map<String, List<List<String>>> playerBingoCards = new ConcurrentHashMap<>();

    // Objeto para serializar y deserializar JSON
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructor del servicio GameService.
     *
     * @param messagingTemplate Plantilla para enviar mensajes vía WebSocket
     */
    public GameService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // =========================================
    // 1. Gestión de Jugadores
    // =========================================

    /**
     * Añade un jugador a una sesión específica. Si la sesión no existe, la crea.
     * Genera un tarjetón único para el jugador y lo envía a través de WebSocket.
     *
     * @param sessionId  ID de la sesión
     * @param playerName Nombre del jugador
     * @throws JsonProcessingException si ocurre un error al procesar JSON
     */
    public void addPlayerToSession(String sessionId, String playerName) throws JsonProcessingException {
        GameSession session = activeSessions.computeIfAbsent(sessionId, key ->
                new GameSession(sessionId, new ArrayList<>(), new ArrayList<>(), false, new HashMap<>())
        );

        synchronized (session) {
            if (!session.getPlayers().contains(playerName)) {
                session.getPlayers().add(playerName);

                // Generar un tarjetón único
                List<List<String>> bingoCard = generateBingoCard();
                session.getBingoCards().put(playerName, bingoCard);
                playerBingoCards.put(playerName, bingoCard); // Almacenar el tarjetón en cache

                // Enviar el tarjetón al jugador a un destino específico
                GameMessage cardMessage = new GameMessage("ASSIGN_CARD", playerName, objectMapper.writeValueAsString(bingoCard));
                messagingTemplate.convertAndSend("/topic/lobby/" + playerName, cardMessage);

                // Actualizar la lista de jugadores en la sesión
                broadcastPlayers(sessionId, session);
                logger.info("Player {} added to session {}", playerName, sessionId);
            } else {
                logger.warn("Player {} is already in session {}", playerName, sessionId);
            }
        }
    }

    /**
     * Elimina un jugador de una sesión específica.
     *
     * @param sessionId  ID de la sesión
     * @param playerName Nombre del jugador
     */
    public void removePlayerFromSession(String sessionId, String playerName) {
        GameSession session = activeSessions.get(sessionId);
        if (session != null) {
            synchronized (session) {
                if (session.getPlayers().remove(playerName)) {
                    playerBingoCards.remove(playerName);
                    session.getBingoCards().remove(playerName);
                    logger.info("Player {} removed from session {}", playerName, sessionId);
                    broadcastPlayers(sessionId, session);
                } else {
                    logger.warn("Player {} not found in session {}", playerName, sessionId);
                }
            }
        } else {
            logger.warn("Session {} not found while trying to remove player {}", sessionId, playerName);
        }
    }

    /**
     * Obtiene la cantidad de jugadores activos en una sesión específica.
     *
     * @param sessionId ID de la sesión
     * @return Número de jugadores
     */
    public int getPlayerCount(String sessionId) {
        GameSession session = activeSessions.get(sessionId);
        return session != null ? session.getPlayers().size() : 0;
    }

    // =========================================
    // 2. Gestión de Sesiones
    // =========================================

    /**
     * Finaliza una sesión específica, limpiando todos los datos asociados y notificando a los jugadores.
     *
     * @param sessionId ID de la sesión a terminar
     */
    public void endSession(String sessionId) {
        GameSession session = activeSessions.get(sessionId);
        if (session != null) {
            synchronized (session) {
                session.setGameOver(true); // Marcar el juego como terminado
                session.getDrawnBalls().clear(); // Limpiar las balotas sorteadas
                session.getPlayers().clear(); // Vaciar la lista de jugadores
                session.setBingoCards(new HashMap<>()); // Limpiar los tarjetones
                activeSessions.remove(sessionId); // Eliminar la sesión activa
                logger.info("Session {} has been ended and cleared", sessionId);

                // Enviar un mensaje a todos los jugadores para redirigirlos al home
                GameMessage message = new GameMessage("SESSION_ENDED", null, "La sesión ha finalizado. Regresa al lobby para iniciar un nuevo juego.");
                messagingTemplate.convertAndSend("/topic/" + sessionId, message);
            }
        } else {
            logger.warn("Attempted to end a non-existent session: {}", sessionId);
        }
    }

    /**
     * Recupera una sesión específica basada en su ID.
     *
     * @param sessionId ID de la sesión
     * @return Objeto GameSession o null si no existe
     */
    public GameSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    // =========================================
    // 3. Gestión de Tarjetones y Validaciones
    // =========================================

    /**
     * Valida si un jugador ha declarado Bingo correctamente según las balotas sorteadas.
     *
     * @param sessionId  ID de la sesión
     * @param playerName Nombre del jugador
     * @return true si el Bingo es válido, false en caso contrario
     */
    public boolean validateBingo(String sessionId, String playerName) {
        GameSession session = activeSessions.get(sessionId);
        if (session == null) throw new IllegalStateException("Session does not exist");

        List<List<String>> bingoCard = session.getBingoCards().get(playerName);
        Set<Integer> drawnBalls = new HashSet<>(session.getDrawnBalls());

        if (bingoCard == null) {
            logger.warn("Player {} has no assigned bingo card", playerName);
            return false;
        }

        return checkRowComplete(bingoCard, drawnBalls)
                || checkColumnComplete(bingoCard, drawnBalls)
                || checkDiagonalComplete(bingoCard, drawnBalls)
                || checkCornersComplete(bingoCard, drawnBalls)
                || checkFullCard(bingoCard, drawnBalls);
    }

    /**
     * Permite a un jugador anunciar Bingo. Si es válido, termina el juego; de lo contrario, suspende al jugador.
     *
     * @param sessionId  ID de la sesión
     * @param playerName Nombre del jugador
     */
    public void announceBingo(String sessionId, String playerName) {
        boolean isValid = validateBingo(sessionId, playerName);

        if (isValid) {
            logger.info("Player {} announced a valid Bingo!", playerName);
            GameMessage message = new GameMessage("GAME_OVER", playerName, null);
            messagingTemplate.convertAndSend("/topic/lobby", message);
            endSession(sessionId);
        } else {
            logger.warn("Player {} announced an invalid Bingo!", playerName);
            suspendPlayer(sessionId, playerName); // Suspender al jugador
        }
    }

    /**
     * Verifica si alguna fila del tarjetón está completa.
     *
     * @param card        Tarjetón de bingo
     * @param drawnBalls  Balotas sorteadas
     * @return true si alguna fila está completa, false en caso contrario
     */
    private boolean checkRowComplete(List<List<String>> card, Set<Integer> drawnBalls) {
        for (List<String> row : card) {
            boolean isComplete = row.stream()
                    .allMatch(cell -> cell.equals("FREE") || drawnBalls.contains(Integer.parseInt(cell.substring(1))));
            if (isComplete) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica si alguna columna del tarjetón está completa.
     *
     * @param card        Tarjetón de bingo
     * @param drawnBalls  Balotas sorteadas
     * @return true si alguna columna está completa, false en caso contrario
     */
    private boolean checkColumnComplete(List<List<String>> card, Set<Integer> drawnBalls) {
        for (int col = 0; col < 5; col++) {
            boolean isComplete = true;
            for (int row = 0; row < 5; row++) {
                String cell = card.get(row).get(col);
                if (!cell.equals("FREE") && !drawnBalls.contains(Integer.parseInt(cell.substring(1)))) {
                    isComplete = false;
                    break;
                }
            }
            if (isComplete) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica si alguna de las diagonales del tarjetón está completa.
     *
     * @param card        Tarjetón de bingo
     * @param drawnBalls  Balotas sorteadas
     * @return true si alguna diagonal está completa, false en caso contrario
     */
    private boolean checkDiagonalComplete(List<List<String>> card, Set<Integer> drawnBalls) {
        boolean leftDiagonal = true;
        boolean rightDiagonal = true;

        for (int i = 0; i < 5; i++) {
            String leftCell = card.get(i).get(i); // Diagonal de arriba a la izquierda a abajo a la derecha
            String rightCell = card.get(i).get(4 - i); // Diagonal de arriba a la derecha a abajo a la izquierda

            if (!leftCell.equals("FREE") && !drawnBalls.contains(Integer.parseInt(leftCell.substring(1)))) {
                leftDiagonal = false;
            }
            if (!rightCell.equals("FREE") && !drawnBalls.contains(Integer.parseInt(rightCell.substring(1)))) {
                rightDiagonal = false;
            }
        }

        return leftDiagonal || rightDiagonal;
    }

    /**
     * Verifica si las cuatro esquinas del tarjetón están completas.
     *
     * @param card        Tarjetón de bingo
     * @param drawnBalls  Balotas sorteadas
     * @return true si las cuatro esquinas están completas, false en caso contrario
     */
    private boolean checkCornersComplete(List<List<String>> card, Set<Integer> drawnBalls) {
        String[] corners = {
                card.get(0).get(0), // Superior izquierda
                card.get(0).get(4), // Superior derecha
                card.get(4).get(0), // Inferior izquierda
                card.get(4).get(4)  // Inferior derecha
        };

        return Arrays.stream(corners)
                .allMatch(cell -> drawnBalls.contains(Integer.parseInt(cell.substring(1))));
    }

    /**
     * Verifica si todas las celdas del tarjetón están completas.
     *
     * @param card        Tarjetón de bingo
     * @param drawnBalls  Balotas sorteadas
     * @return true si todo el tarjetón está completo, false en caso contrario
     */
    private boolean checkFullCard(List<List<String>> card, Set<Integer> drawnBalls) {
        return card.stream()
                .flatMap(List::stream)
                .allMatch(cell -> cell.equals("FREE") || drawnBalls.contains(Integer.parseInt(cell.substring(1))));
    }

    // =========================================
    // 4. Gestión de Balotas
    // =========================================

    /**
     * Sortea una nueva balota para una sesión específica y notifica a los jugadores.
     *
     * @param sessionId ID de la sesión
     */
    public void drawNewBall(String sessionId) {
        GameSession session = activeSessions.get(sessionId);
        if (session == null || session.isGameOver()) {
            logger.warn("No se pudo sortear balotas: sesión no existe o está terminada.");
            return;
        }

        Random random = new Random();
        Integer newBall = null;

        synchronized (session) {
            if (session.getDrawnBalls().size() >= 75) {
                logger.info("Todas las balotas ya han sido sorteadas para la sesión {}", sessionId);
                return;
            }
            while (true) {
                int ball = random.nextInt(75) + 1;
                if (!session.getDrawnBalls().contains(ball)) {
                    session.getDrawnBalls().add(ball);
                    newBall = ball;
                    break;
                }
            }
        }

        if (newBall != null) {
            logger.info("Balota sorteada automáticamente para la sesión {}: {}", sessionId, newBall);
            try {
                // Serializar la balota a JSON
                String ballJson = objectMapper.writeValueAsString(Collections.singletonList(newBall));
                GameMessage message = new GameMessage("NEW_BALLS", null, ballJson);
                messagingTemplate.convertAndSend("/topic/lobby", message); // Enviar a /topic/lobby
                logger.info("Mensaje NEW_BALLS enviado a /topic/lobby: {}", ballJson);
            } catch (JsonProcessingException e) {
                logger.error("Error al serializar la balota sorteada: {}", e.getMessage());
            }
        }
    }

    /**
     * Método programado para sortear una balota automáticamente cada 5 segundos.
     */
    @Scheduled(fixedRate = 5000) // Cada 5 segundos
    public void scheduledDrawBall() {
        logger.info("Ejecutando scheduledDrawBall");

        activeSessions.keySet().forEach(sessionId -> {
            GameSession session = activeSessions.get(sessionId);
            if (session != null && !session.isGameOver()) {
                drawNewBall(sessionId);
            }
        });
    }

    // =========================================
    // 5. Comunicación con los Clientes
    // =========================================

    /**
     * Envía una actualización de la lista de jugadores a todos los clientes en una sesión específica.
     *
     * @param sessionId ID de la sesión
     * @param session   Objeto GameSession
     */
    private void broadcastPlayers(String sessionId, GameSession session) {
        GameMessage message = new GameMessage("UPDATE_PLAYERS", null);
        message.setPlayers(new ArrayList<>(session.getPlayers()));
        messagingTemplate.convertAndSend("/topic/" + sessionId, message);
        logger.info("Broadcasted players for session {}", sessionId);
    }

    /**
     * Suspende a un jugador que ha declarado un Bingo inválido y notifica a los demás jugadores.
     *
     * @param sessionId  ID de la sesión
     * @param playerName Nombre del jugador
     */
    public void suspendPlayer(String sessionId, String playerName) {
        GameSession session = activeSessions.get(sessionId);

        if (session != null) {
            synchronized (session) {
                if (session.getPlayers().remove(playerName)) {
                    session.getBingoCards().remove(playerName); // Eliminar el tarjetón del jugador
                    playerBingoCards.remove(playerName); // Limpiar el cache del tarjetón
                    logger.info("Player {} has been suspended from session {}", playerName, sessionId);

                    // Notificar al jugador suspendido
                    GameMessage suspensionMessage = new GameMessage(
                            "PLAYER_SUSPENDED",
                            playerName,
                            "Has sido suspendido por declarar un Bingo inválido. Serás redirigido al lobby."
                    );
                    messagingTemplate.convertAndSendToUser(playerName, "/topic/lobby/" + playerName, suspensionMessage);

                    // Notificar a los demás jugadores sobre la suspensión
                    GameMessage updatePlayersMessage = new GameMessage(
                            "UPDATE_PLAYERS",
                            null,
                            playerName + " ha sido suspendido por declarar un Bingo inválido."
                    );
                    broadcastPlayers(sessionId, session); // Actualizar la lista de jugadores activos
                    messagingTemplate.convertAndSend("/topic/lobby", updatePlayersMessage);
                } else {
                    logger.warn("Player {} not found in session {} while attempting suspension", playerName, sessionId);
                }
            }
        } else {
            logger.warn("Attempted to suspend player {} from non-existent session {}", playerName, sessionId);
        }
    }

    // =========================================
    // 6. Métodos Auxiliares
    // =========================================

    /**
     * Genera un tarjetón de bingo único para un jugador.
     *
     * @return Lista bidimensional que representa el tarjetón
     */
    private List<List<String>> generateBingoCard() {
        Random random = new Random();
        String[] columns = {"B", "I", "N", "G", "O"};
        List<List<String>> card = new ArrayList<>();

        for (int i = 0; i < columns.length; i++) {
            Set<Integer> columnNumbers = new HashSet<>();
            int start = i * 15 + 1;
            int end = start + 14;

            while (columnNumbers.size() < 5) {
                int num = random.nextInt(end - start + 1) + start;
                columnNumbers.add(num);
            }

            List<String> column = new ArrayList<>();
            for (int num : columnNumbers) {
                column.add(columns[i] + num);
            }

            card.add(column);
        }

        // Establecer el centro como "FREE"
        card.get(2).set(2, "FREE");
        return card;
    }

    // =========================================
    // 7. Manejo de Excepciones y Logs
    // =========================================

    // Todos los métodos ya manejan logs y excepciones apropiadamente.
    // No se requiere código adicional aquí.

    // =========================================
    // 8. Gestión de Estados del Juego
    // =========================================

    // Ya implementado en otros métodos como getSession, endSession, etc.

}
