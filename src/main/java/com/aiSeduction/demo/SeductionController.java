package com.aiSeduction.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
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
    // Endpoint 1: Generate Random Social Scenario Prompt
    // =======================
    @GetMapping("/prompts/random")
    public Map<String, String> getRandomPrompt() {
        Map<String, String> result = new HashMap<>();
        try {
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

            String aiPrompt = "Generate a single, concise prompt for practicing confident, engaging conversation starters in a seduction or social skills context. " +
                             "The prompt should describe a unique and highly random social situation where the user can make a confident statement to initiate dialogue. " +
                             "Emphasize creativity and unpredictability in the setting, people, or objects involved (e.g., unusual locations, quirky activities, or unexpected encounters). " +
                             "Avoid repetitive or generic scenarios like bars or coffee shops. Return only the prompt text, enclosed in quotes, e.g., \"You notice someone juggling fruit at a farmer's market. Make a confident statement to start a conversation.\"";

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
            String prompt = contentNode.asText().replaceAll("^\"|\"$", "");

            result.put("question", prompt);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("question", "Error generating prompt: " + e.getMessage());
        }
        return result;
    }

    // =======================
    // Endpoint 2: Evaluate Answer
    // =======================
    @PostMapping("/ai/evaluate")
    public Map<String, Object> evaluateAnswer(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "");
        String answer = request.getOrDefault("answer", "");

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("question", question);
        responseMap.put("answer", answer);

        if (question.isEmpty() || answer.isEmpty()) {
            responseMap.put("score", 0);
            responseMap.put("feedback", "Missing 'question' or 'answer' in request.");
            responseMap.put("correction", "");
            return responseMap;
        }

        try {
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

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

            int score = 0;
            Pattern scorePattern = Pattern.compile("Score:\\s*(\\d+)/20");
            Matcher scoreMatcher = scorePattern.matcher(aiResponse);
            if (scoreMatcher.find()) {
                try {
                    score = Integer.parseInt(scoreMatcher.group(1));
                } catch (NumberFormatException ignored) {}
            }

            String correction = "";
            if (aiResponse.contains("Corrected/Improved Version:")) {
                String[] parts = aiResponse.split("Corrected/Improved Version:");
                if (parts.length > 1) {
                    String correctionSection = parts[1].trim();
                    Pattern optionPattern = Pattern.compile("\\*\\*Option 1[^:]*?:\\*\\*\\s*\"([^\"]+)\"");
                    Matcher optionMatcher = optionPattern.matcher(correctionSection);
                    if (optionMatcher.find()) {
                        correction = optionMatcher.group(1).trim();
                    } else {
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

    // =======================
    // Endpoint 3: Convert Question to Statement
    // =======================
    @PostMapping("/question-to-statement")
    public Map<String, String> convertQuestionToStatement(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "");
        Map<String, String> responseMap = new HashMap<>();
        responseMap.put("question", question);

        if (question.isEmpty()) {
            responseMap.put("statement", "Error: Missing 'question' in request.");
            return responseMap;
        }

        try {
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

            String aiPrompt = String.format(
                    "You are an AI seduction coach. The user wants to convert an interview-style question into a confident, engaging statement or assumption for a seduction or social skills context, particularly for interactions with women. " +
                    "Avoid interrogative tones that feel like an interview. Transform the provided question into a single, concise statement that is bold, charming, and invites conversation. " +
                    "Return only the transformed statement, enclosed in quotes, e.g., \"You seem like someone with a great story behind that book you're reading.\" " +
                    "Question: %s",
                    question.replace("\"", "\\\"")
            );

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
            String statement = contentNode.asText().replaceAll("^\"|\"$", "");

            responseMap.put("statement", statement);
        } catch (Exception e) {
            e.printStackTrace();
            responseMap.put("statement", "Error converting question to statement: " + e.getMessage());
        }

        return responseMap;
    }

    // =======================
    // Endpoint 4: Generate Random Day-to-Day Question
    // =======================
    @GetMapping("/questions/random")
    public Map<String, String> getRandomDayToDayQuestion() {
        Map<String, String> result = new HashMap<>();
        try {
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

            String aiPrompt = "Generate a single, concise, interview-style question that might be asked in a casual social interaction, suitable for a seduction or social skills context with women. " +
                             "The question should be simple, day-to-day, and conversational, e.g., 'What do you like?' or 'Do you enjoy reading?' " +
                             "Return only the question text, enclosed in quotes, e.g., \"What do you like?\"";

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
            String question = contentNode.asText().replaceAll("^\"|\"$", "");

            result.put("question", question);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("question", "Error generating question: " + e.getMessage());
        }
        return result;
    }
}