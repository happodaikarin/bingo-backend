package com.granbuda.bingo.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.granbuda.bingo.model.GameMessage;
import com.granbuda.bingo.model.GameSession;
import com.granbuda.bingo.service.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controlador que maneja las interacciones de WebSocket para el juego de Bingo.
 * Gestiona la conexión de jugadores, inicio del juego, temporizador, sorteo de balotas,
 * sincronización de estados y suspensión de jugadores.
 */
@Controller
public class GameController {

    private static final Logger logger = LoggerFactory.getLogger(GameController.class);
    
    // Dependencias inyectadas
    private final SimpMessagingTemplate messagingTemplate;
    private final GameService gameService;
    private final ObjectMapper objectMapper;

    // Variables para la gestión del temporizador
    private Timer currentTimer;
    private boolean extendUsed = false;    // Control para una única extensión
    private boolean extendQueued = false;  // Indica si hay una extensión en cola

    /**
     * Constructor del GameController.
     *
     * @param messagingTemplate Plantilla para enviar mensajes vía WebSocket
     * @param gameService       Servicio que maneja la lógica del juego de Bingo
     * @param objectMapper      Objeto para la serialización/deserialización JSON
     */
    public GameController(SimpMessagingTemplate messagingTemplate, GameService gameService, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.gameService = gameService;
        this.objectMapper = objectMapper;
    }

    // =========================================
    // 1. Manejo de Eventos de WebSocket
    // =========================================

    /**
     * Maneja la conexión de un jugador al buscar un juego.
     *
     * @param username        Nombre del jugador
     * @param headerAccessor  Accesorio de encabezados de mensajes
     * @throws JsonProcessingException Si ocurre un error al procesar JSON
     */
    @MessageMapping("/search_game")
    public void searchGame(@Payload String username, SimpMessageHeaderAccessor headerAccessor) throws JsonProcessingException {
        String sessionId = "lobby"; // Asumimos una única sesión "lobby"

        if (username == null || username.trim().isEmpty()) {
            logger.warn("Nombre de usuario inválido recibido");
            return;
        }

        gameService.addPlayerToSession(sessionId, username); // Agregar jugador a la sesión
        headerAccessor.getSessionAttributes().put("username", username);
    }

    /**
     * Maneja la desconexión de un jugador del WebSocket.
     *
     * @param event Evento de desconexión de la sesión WebSocket
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        if (username != null) {
            logger.info("Jugador desconectado: {}", username);
            gameService.removePlayerFromSession("lobby", username);
        }
    }

    /**
     * Maneja la solicitud de cierre de sesión de un jugador.
     *
     * @param username Nombre del jugador que solicita cerrar sesión
     */
    @MessageMapping("/logout")
    public void logoutPlayer(@Payload String username) {
        gameService.removePlayerFromSession("lobby", username);
        logger.info("Jugador {} ha cerrado sesión", username);
    }

    // =========================================
    // 2. Inicio y Gestión del Juego
    // =========================================

    /**
     * Maneja la solicitud de inicio del juego por parte de un jugador.
     *
     * @param username Nombre del jugador que inicia el juego
     */
    @MessageMapping("/start_game")
    public void startGame(@Payload String username) {
        String sessionId = "lobby"; // Asumimos una única sesión "lobby"

        if (gameService.getPlayerCount(sessionId) > 2) {
            logger.info("Juego iniciado por {}", username);
            messagingTemplate.convertAndSend("/topic/lobby/" + sessionId, new GameMessage("GAME_STARTED", username, null));
        } else {
            logger.warn("Intento de iniciar el juego con jugadores insuficientes.");
            messagingTemplate.convertAndSend("/topic/lobby", new GameMessage("NOTIFY", null, "Se requieren al menos 3 jugadores para iniciar el juego."));
        }
    }

    /**
     * Maneja la solicitud de inicio del temporizador para el juego.
     *
     * @param username Nombre del jugador que inicia el temporizador
     */
    @MessageMapping("/start_timer")
    public synchronized void startTimer(@Payload String username) {
        String sessionId = "lobby"; // Asumimos una única sesión "lobby"

        if (gameService.getPlayerCount(sessionId) < 3) {
            messagingTemplate.convertAndSend("/topic/lobby", new GameMessage("NOTIFY", null, "Se necesitan al menos 3 jugadores para iniciar el juego."));
            logger.warn("Temporizador no iniciado: jugadores insuficientes.");
            return;
        }

        if (currentTimer != null) {
            // Si ya hay un temporizador activo y no se ha usado la extensión
            if (!extendUsed && !extendQueued) {
                extendUsed = true;
                extendQueued = true; // Marcar la extensión en cola
                messagingTemplate.convertAndSend("/topic/lobby", new GameMessage("TIMER_QUEUED", null, "El tiempo se extenderá automáticamente por 30 segundos al finalizar."));
                logger.info("Extensión del temporizador en cola.");
            }
            return;
        }

        // Inicializar un nuevo temporizador
        extendUsed = false; // Reiniciar el control al iniciar un nuevo temporizador
        extendQueued = false;
        messagingTemplate.convertAndSend("/topic/lobby", new GameMessage("TIMER_STARTED", null, "El juego comenzará en 30 segundos."));
        logger.info("Temporizador iniciado por {}", username);
        startOrExtendTimer(username, 30); // Iniciar temporizador con 30 segundos
    }

    /**
     * Inicia o extiende el temporizador del juego.
     *
     * @param username    Nombre del jugador que inicia o extiende el temporizador
     * @param initialTime Tiempo inicial del temporizador en segundos
     */
    private void startOrExtendTimer(String username, int initialTime) {
        currentTimer = new Timer();
        AtomicInteger timeRemaining = new AtomicInteger(initialTime);

        currentTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                int remaining = timeRemaining.decrementAndGet();
                if (remaining <= 0) {
                    // Finalizar el temporizador actual
                    currentTimer.cancel();
                    currentTimer = null;

                    if (extendQueued) {
                        extendQueued = false; // Consumir la extensión
                        messagingTemplate.convertAndSend("/topic/lobby", new GameMessage("TIMER_EXTENDED", null, "El tiempo se ha extendido automáticamente por 30 segundos."));
                        logger.info("Temporizador extendido automáticamente.");
                        startOrExtendTimer(username, 30); // Reiniciar el temporizador
                    } else {
                        // Comenzar el juego si no hay extensiones pendientes
                        messagingTemplate.convertAndSend("/topic/lobby", new GameMessage("GAME_STARTED", username, null));
                        logger.info("Temporizador finalizado. Juego iniciado.");
                    }
                } else {
                    logger.debug("Temporizador: {} segundos restantes", remaining);
                }
            }
        }, 1000, 1000); // Actualizar cada segundo
    }

    // =========================================
    // 3. Sorteo de Balotas
    // =========================================

    /**
     * Maneja la solicitud de sortear una balota.
     */
    @MessageMapping("/draw_ball")
    public void drawBall() {
        try {
            gameService.drawNewBall("lobby");
            logger.info("Balota sorteada manualmente.");
        } catch (IllegalStateException e) {
            logger.warn("Error al sortear balota: {}", e.getMessage());
            messagingTemplate.convertAndSend("/topic/lobby", new GameMessage("ERROR", null, "No se pudo sortear una nueva balota."));
        }
    }

    // =========================================
    // 4. Anuncio de Bingo
    // =========================================

    /**
     * Maneja la solicitud de un jugador para anunciar Bingo.
     *
     * @param username Nombre del jugador que anuncia Bingo
     */
    @MessageMapping("/announce_bingo")
    public void announceBingo(@Payload String username) {
        gameService.announceBingo("lobby", username);
        logger.info("Jugador {} ha anunciado Bingo.", username);
    }

    // =========================================
    // 5. Sincronización de Estado
    // =========================================

    /**
     * Maneja la solicitud de sincronización de estado para un jugador.
     *
     * @param username        Nombre del jugador que solicita la sincronización
     * @param headerAccessor  Accesorio de encabezados de mensajes
     * @throws JsonProcessingException Si ocurre un error al procesar JSON
     */
    @MessageMapping("/sync_state")
    public void syncState(@Payload String username, SimpMessageHeaderAccessor headerAccessor) throws JsonProcessingException {
        String sessionId = "lobby"; // Usar la sesión actual del jugador
        GameSession session = gameService.getSession(sessionId);

        if (session != null) {
            List<Integer> drawnBalls = session.getDrawnBalls(); // Obtener las balotas sorteadas
            List<List<String>> bingoCard = session.getBingoCards().get(username); // Tarjetón del jugador

            Map<String, Object> state = new HashMap<>();
            state.put("drawnBalls", drawnBalls);
            state.put("bingoCard", bingoCard);

            GameMessage message = new GameMessage("SYNC_STATE", username, objectMapper.writeValueAsString(state));
            messagingTemplate.convertAndSendToUser(username, "/topic/lobby/" + username, message);
            logger.info("Estado sincronizado para jugador {}", username);
        } else {
            logger.warn("No se encontró la sesión para el usuario: {}", username);
            messagingTemplate.convertAndSendToUser(username, "/topic/lobby/" + username, new GameMessage("ERROR", null, "No se encontró la sesión del juego."));
        }
    }

}