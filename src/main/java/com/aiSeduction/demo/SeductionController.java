package com.aiSeduction.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class SeductionController {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // =======================
    // State-based prompts
    // =======================
    private final List<String> prompts = List.of(
            "You see someone reading your favorite book. Make a confident statement to start a conversation.",
            "You notice someone wearing a band t-shirt you love. Turn it into a playful, engaging comment.",
            "You are at a café and see someone enjoying a unique coffee. Create a memorable statement to connect.",
            "At a party, someone laughs at your joke. Use it to continue the conversation in a charming way.",
            "You spot someone looking at the same artwork in a gallery. Make an interesting comment to start dialogue."
    );

    // =======================
    // Endpoint 1: Generate Random Prompt
    // =======================
    @GetMapping("/prompts/random")
    public Map<String, String> getRandomPrompt() {
        Map<String, String> result = new HashMap<>();
        int idx = new Random().nextInt(prompts.size());
        result.put("question", prompts.get(idx));
        return result;
    }

    // =======================
    // Endpoint 2: Evaluate Answer + Correction
    // =======================
    @PostMapping("/ai/evaluate")
    public Map<String, Object> evaluateAnswer(@RequestBody Map<String, String> request) {
        // Get question and answer from request
        String question = request.getOrDefault("question", "");
        String answer = request.getOrDefault("answer", "");

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("question", question);
        responseMap.put("answer", answer);

        // If question or answer is empty, return error
        if (question.isEmpty() || answer.isEmpty()) {
            responseMap.put("score", 0);
            responseMap.put("feedback", "Missing 'question' or 'answer' in request.");
            responseMap.put("correction", "");
            return responseMap;
        }

        try {
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

            // AI Prompt: evaluate + correct
            String aiPrompt = String.format(
                    "You are an AI seduction coach. The user is practicing turning situations into confident statements (\"state\"). " +
                    "Evaluate their answer from 1 to 20. Provide: " +
                    "- A numeric score in the format 'Score: X/20' " +
                    "- Detailed feedback " +
                    "- A corrected/improved version of the answer under the heading 'Corrected/Improved Version:' with at least one option " +
                    "Prompt: %s " +
                    "User Answer: %s",
                    question.replace("\"", "\\\""),
                    answer.replace("\"", "\\\"")
            );

            // Create JSON payload with proper escaping
            String jsonPayload = String.format(
                    "{\"contents\": [{\"parts\": [{\"text\": \"%s\"}]}]}",
                    aiPrompt.replace("\"", "\\\"")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", geminiApiKey);

            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode contentNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
            String aiResponse = contentNode.asText();

            // Extract numeric score using regex
            int score = 0;
            Pattern scorePattern = Pattern.compile("Score:\\s*(\\d+)/20");
            Matcher scoreMatcher = scorePattern.matcher(aiResponse);
            if (scoreMatcher.find()) {
                try {
                    score = Integer.parseInt(scoreMatcher.group(1));
                } catch (NumberFormatException ignored) {}
            }

            // Extract correction after "Corrected/Improved Version:"
            String correction = "";
            if (aiResponse.contains("Corrected/Improved Version:")) {
                String[] parts = aiResponse.split("Corrected/Improved Version:");
                if (parts.length > 1) {
                    // Extract the first option (e.g., Option 1) if multiple are provided
                    String correctionSection = parts[1].trim();
                    Pattern optionPattern = Pattern.compile("\\*\\*Option 1[^:]*?:\\*\\*\\s*\"([^\"]+)\"");
                    Matcher optionMatcher = optionPattern.matcher(correctionSection);
                    if (optionMatcher.find()) {
                        correction = optionMatcher.group(1).trim();
                    } else {
                        // Fallback: take the first line after the heading
                        String[] lines = correctionSection.split("\n");
                        for (String line : lines) {
                            if (line.trim().startsWith("\"") && line.trim().endsWith("\"")) {
                                correction = line.trim().replaceAll("^\"|\"$", "");
                                break;
                            }
                        }
                    }
                }
            }

            responseMap.put("score", score);
            responseMap.put("feedback", aiResponse);
            responseMap.put("correction", correction);

        } catch (Exception e) {
            e.printStackTrace();
            responseMap.put("score", 0);
            responseMap.put("feedback", "Error contacting Gemini AI: " + e.getMessage());
            responseMap.put("correction", "");
        }

        return responseMap;
    }
}