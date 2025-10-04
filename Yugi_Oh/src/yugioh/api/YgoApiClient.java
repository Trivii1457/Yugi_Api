package yugioh.api;

import yugioh.model.Card;

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
import java.util.List;
//import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YgoApiClient {

    private static final String BASE_URL = "https://db.ygoprodeck.com/api/v7";

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
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
        .header("User-Agent", "YugiApiClient/1.0 (+https://example)")
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
        // Find the "data" array and extract each JSON object within it.
        int dataIndex = json.indexOf("\"data\"");
        if (dataIndex < 0) {
            return List.of();
        }
        int arrayStart = json.indexOf('[', dataIndex);
        if (arrayStart < 0) {
            return List.of();
        }
        int idx = arrayStart + 1;
        List<Card> cards = new ArrayList<>();
        while (idx < json.length()) {
            // skip whitespace
            while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
            if (idx >= json.length() || json.charAt(idx) == ']') break;
            if (json.charAt(idx) != '{') {
                idx++;
                continue;
            }
            int depth = 0;
            int objStart = idx;
            while (idx < json.length()) {
                char c = json.charAt(idx);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        idx++; // include closing brace
                        break;
                    }
                }
                idx++;
            }
            if (depth != 0) break; // malformed
            String objJson = json.substring(objStart, idx);
            Card card = parseCardObject(objJson);
            if (card != null) cards.add(card);
            // skip commas/spaces
            while (idx < json.length() && (Character.isWhitespace(json.charAt(idx)) || json.charAt(idx) == ',')) idx++;
        }
        return cards;
    }

    private Card parseCardObject(String objJson) {
        int id = extractInt(objJson, "\"id\"\s*:\s*(\\d+)", 0);
        String name = extractString(objJson, "\"name\"\s*:\s*\"(.*?)\"");
        if (name == null) name = "Desconocida";
        String type = extractString(objJson, "\"type\"\s*:\s*\"(.*?)\"");
        if (type == null) type = "";
        int atk = extractInt(objJson, "\"atk\"\s*:\s*(null|\\d+)", 0);
        int def = extractInt(objJson, "\"def\"\s*:\s*(null|\\d+)", 0);
        String desc = extractString(objJson, "\"desc\"\s*:\s*\"(.*?)\"");
        if (desc == null) desc = "";
        String imageUrl = extractImageUrlFromObject(objJson);
        return new Card(id, name, type, atk, def, desc, imageUrl);
    }

    private int extractInt(String json, String regex, int fallback) {
        Pattern p = Pattern.compile(regex, Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (m.find()) {
            String g = m.group(1);
            if (g == null || g.equals("null")) return fallback;
            try {
                return Integer.parseInt(g);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private String extractString(String json, String regex) {
        Pattern p = Pattern.compile(regex, Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (m.find()) {
            String g = m.group(1);
            return unescapeJsonString(g);
        }
        return null;
    }

    private String extractImageUrlFromObject(String objJson) {
        // Find card_images array and then first image_url
        int ci = objJson.indexOf("\"card_images\"");
        if (ci < 0) return "";
        int start = objJson.indexOf('[', ci);
        if (start < 0) return "";
        int idx = start + 1;
        // find first object inside array
        while (idx < objJson.length()) {
            while (idx < objJson.length() && Character.isWhitespace(objJson.charAt(idx))) idx++;
            if (idx >= objJson.length() || objJson.charAt(idx) == ']') break;
            if (objJson.charAt(idx) != '{') { idx++; continue; }
            int depth = 0;
            int objStart = idx;
            while (idx < objJson.length()) {
                char c = objJson.charAt(idx);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { idx++; break; }
                }
                idx++;
            }
            String firstImageObj = objJson.substring(objStart, idx);
            String url = extractString(firstImageObj, "\"image_url\"\s*:\s*\"(.*?)\"");
            return url == null ? "" : url;
        }
        return "";
    }

    private String unescapeJsonString(String s) {
        if (s == null) return null;
        // minimal unescape for common escapes
        return s.replaceAll("\\\\\"", "\"")
                .replaceAll("\\\\/", "/")
                .replaceAll("\\\\n", "\n")
                .replaceAll("\\\\r", "\r")
                .replaceAll("\\\\t", "\t")
                .replaceAll("\\\\\\\\", "\\");
    }
}
