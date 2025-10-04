# Yugi_Api

Aplicación de escritorio en Java 11 que consume la API de YGOProDeck para recrear un duelo sencillo de Yu-Gi-Oh! como parte del laboratorio #1 de Desarrollo de Software III.


###Ejecuta la clase `yugioh.Main`.

## Diseño

- **Capas:**
	- `yugioh.api` encapsula el cliente HTTP (`YgoApiClient`) y el parseo ligero de JSON usando Nashorn para mantener el proyecto sin dependencias externas.
	- `yugioh.model` contiene las entidades (`Card`, `CardSelection`, `CardPosition`) utilizadas en todo el dominio.
	- `yugioh.core` incluye la lógica del duelo (`Duel`) y los contratos de eventos (`BattleListener`).
	- `yugioh.ui` alberga la interfaz Swing (`GameWindow`), que escucha los eventos del duelo y mantiene el hilo de la EDT libre durante las peticiones.

- **Flujo principal:**
	1. El usuario presiona "Cargar cartas". La UI dispara tareas asíncronas que obtienen 3 cartas *Monster* para el jugador y la IA mediante `YgoApiClient`.
	2. Al terminar, se muestran las cartas (imagen, ATK, DEF) y se inicializa un `Duel` con un turno inicial aleatorio.
	3. Cada vez que el jugador elige una carta y su posición, el `Duel` resuelve la ronda, actualiza el marcador y notifica a la UI mediante `BattleListener`.
	4. El primer duelista en ganar 2 rondas o quedarse con más puntos al agotarse las cartas es declarado ganador.

## Capturas

Incluye tus capturas de pantalla en la carpeta `docs/` o directamente en esta sección cuando las tengas listas (por ejemplo, `![Duelo](docs/duelo.png)`).
