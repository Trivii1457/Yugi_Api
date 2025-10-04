package yugioh.api;

import yugioh.model.Card;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class YgoApiClient {

    private static final String BASE_URL = "https://db.ygoprodeck.com/api/v7";
    private static final ScriptEngine ENGINE = new ScriptEngineManager().getEngineByName("javascript");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Optional<Card> fetchCardByName(String cardName) throws IOException, InterruptedException {
        String encodedName = URLEncoder.encode(cardName, StandardCharsets.UTF_8);
        String url = BASE_URL + "/cardinfo.php?name=" + encodedName;
        String body = performRequest(url);
        List<Card> cards = parseCards(body);
        return cards.isEmpty() ? Optional.empty() : Optional.of(cards.get(0));
    }

    public List<Card> fetchRandomMonsterCards(int desiredCount) throws IOException, InterruptedException {
        List<Card> cards = new ArrayList<>();
        int attempts = 0;
        while (cards.size() < desiredCount && attempts < desiredCount * 8) {
            attempts++;
            String body = performRequest(BASE_URL + "/randomcard.php");
            List<Card> fetched = parseCards(body);
            for (Card card : fetched) {
                if (card.isMonster()) {
                    cards.add(card);
                }
                if (cards.size() == desiredCount) {
                    break;
                }
            }
        }
        if (cards.size() < desiredCount) {
            throw new IOException("No se pudieron obtener cartas Monster aleatorias tras " + attempts + " intentos");
        }
        Collections.shuffle(cards, ThreadLocalRandom.current());
        return cards;
    }

    private String performRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("No se pudo cargar la carta: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private List<Card> parseCards(String json) throws IOException {
        if (ENGINE == null) {
            throw new IOException("Motor de JavaScript no disponible para parsear JSON");
        }
        try {
            Object raw;
            synchronized (ENGINE) {
                raw = ENGINE.eval("Java.asJSONCompatible(" + json + ")");
            }
            if (!(raw instanceof Map)) {
                return List.of();
            }
            Map<?, ?> obj = (Map<?, ?>) raw;
            Object dataObj = obj.get("data");
            if (!(dataObj instanceof List)) {
                return List.of();
            }
            List<?> data = (List<?>) dataObj;
            List<Card> cards = new ArrayList<>();
            for (Object item : data) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<?, ?> cardMap = (Map<?, ?>) item;
                Card card = mapToCard(cardMap);
                if (card != null) {
                    cards.add(card);
                }
            }
            return cards;
        } catch (ScriptException e) {
            throw new IOException("Error parseando JSON", e);
        }
    }

    private Card mapToCard(Map<?, ?> cardMap) {
        Object idObj = cardMap.get("id");
        int id = idObj instanceof Number ? ((Number) idObj).intValue() : 0;
        String name = Objects.toString(cardMap.get("name"), "Desconocida");
        String type = Objects.toString(cardMap.get("type"), "");
        int atk = parseStat(cardMap.get("atk"));
        int def = parseStat(cardMap.get("def"));
        String desc = Objects.toString(cardMap.get("desc"), "");
        String imageUrl = extractImageUrl(cardMap.get("card_images"));
        return new Card(id, name, type, atk, def, desc, imageUrl);
    }

    private int parseStat(Object statObj) {
        if (statObj instanceof Number number) {
            return number.intValue();
        }
        try {
            return statObj != null ? Integer.parseInt(statObj.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractImageUrl(Object imagesObj) {
        if (!(imagesObj instanceof List<?> images)) {
            return "";
        }
        if (images.isEmpty()) {
            return "";
        }
        Object first = images.get(0);
        if (first instanceof Map<?, ?> map) {
            Object url = map.get("image_url");
            if (url != null) {
                return url.toString();
            }
        }
        if (first instanceof LinkedHashMap) {
            Object url = ((LinkedHashMap<?, ?>) first).get("image_url");
            return url != null ? url.toString() : "";
        }
        return "";
    }
}
