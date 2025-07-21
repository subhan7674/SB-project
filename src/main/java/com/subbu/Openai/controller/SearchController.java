package com.subbu.Openai.controller;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpSession;

@Controller
public class SearchController {

	@Value("${openai.api.key}")
	private String apiKey;

public boolean isPrime(int number) {
    if (number <= 1) {
        return false; // Numbers less than or equal to 1 are not prime
    }
    for (int i = 2; i <= Math.sqrt(number); i++) {
        if (number % i == 0) {
            return false; // If divisible by any number other than 1 and itself, not prime
        }
    }
    return true; // Otherwise, the number is prime
}

	private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";

	@GetMapping("/")
	public String searchPage(HttpSession session, Model model) {
		List<String[]> chatHistory = (List<String[]>) session.getAttribute("chatHistory");

		if (chatHistory == null) {
			chatHistory = new ArrayList<>();
		}
		
		model.addAttribute("chatHistory", chatHistory);
		return "index";
	}

	@GetMapping("/clear")
	public String clearChatHistory(HttpSession session) {
		session.removeAttribute("chatHistory");
		return "redirect:/";
	}

	@PostMapping("/search")
	public String search(@RequestParam("query") String query, HttpSession session, Model model) {
		// Retrieve chat history or initialize it
		List<String[]> chatHistory = (List<String[]>) session.getAttribute("chatHistory");
		if (chatHistory == null) {
			chatHistory = new ArrayList<>();
		}

		// Convert chat history into a context for the AI
		String context = buildContext(chatHistory, query);

		// Get the AI response
		String response = callOpenRouter(context);

		// Add the current query and response to the chat history
		chatHistory.add(new String[] { query, response });
		session.setAttribute("chatHistory", chatHistory);

		// Update the model with the updated chat history
		model.addAttribute("chatHistory", chatHistory);

		return "index";
	}

	private String callOpenRouter(String context) {
		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + apiKey);
		headers.set("Content-Type", "application/json");
		headers.set("HTTP-Referer", "http://localhost:8080");
		headers.set("X-Title", "Java Spring App");

		// Use a proper JSON builder or string escape mechanism
		String requestBody = """
				{
				    "model": "openai/gpt-3.5-turbo",
				    "messages": [
				        {"role": "user", "content": "%s"}
				    ]
				}
				""".formatted(context.replace("\"", "\\\"")); // Escaping quotes inside the context

		HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(OPENROUTER_API_URL, HttpMethod.POST, request,
					String.class);

			JSONObject jsonResponse = new JSONObject(response.getBody());
			JSONArray choices = jsonResponse.getJSONArray("choices");
			String message = choices.getJSONObject(0).getJSONObject("message").getString("content");

			return message.trim();

		} catch (HttpClientErrorException e) {
			if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
				return "⚠️ Rate limit exceeded. Try again in a moment.";
			} else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
				return "❌ Invalid or missing OpenRouter API key.";
			} else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
				return "❗ Invalid JSON structure. Please check your query or system configuration.";
			} else {
				return "❗ Error from OpenRouter: " + e.getResponseBodyAsString();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "⚠️ Unexpected error: " + e.getMessage();
		}
	}

	private String buildContext(List<String[]> chatHistory, String query) {
		StringBuilder contextBuilder = new StringBuilder();

		// Append previous queries and responses
		for (String[] chat : chatHistory) {
			contextBuilder.append("User: ").append(chat[0]).append("\n");
			contextBuilder.append("AI: ").append(chat[1]).append("\n");
		}

		// Append the new query
		contextBuilder.append("User: ").append(query);

		return contextBuilder.toString();
	}
}
