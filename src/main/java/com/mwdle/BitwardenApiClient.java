package com.mwdle;

// Standard Jackson import
import com.fasterxml.jackson.databind.ObjectMapper;

// Standard Apache HttpClient imports
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map; // <-- Added import for Map

public class BitwardenApiClient {

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String identityUrl;
    private final String accessToken;

    public BitwardenApiClient(String serverUrl, String clientId, String clientSecret) throws IOException {
        this.httpClient = HttpClientBuilder.create().build();
        this.objectMapper = new ObjectMapper();
        this.identityUrl = (serverUrl != null && !serverUrl.isEmpty()) ? serverUrl : "https://identity.bitwarden.com";

        this.accessToken = login(clientId, clientSecret);
        if (this.accessToken == null || this.accessToken.isEmpty()) {
            throw new IOException("Failed to authenticate with Bitwarden API.");
        }
    }

    private String login(String clientId, String clientSecret) throws IOException {
        HttpPost request = new HttpPost(this.identityUrl + "/connect/token");
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        request.setEntity(new UrlEncodedFormEntity(Arrays.asList(
                new BasicNameValuePair("grant_type", "client_credentials"),
                new BasicNameValuePair("scope", "api"),
                new BasicNameValuePair("client_id", clientId),
                new BasicNameValuePair("client_secret", clientSecret)
        ), StandardCharsets.UTF_8));

        return httpClient.execute(request, response -> {
            if (response.getCode() >= 200 && response.getCode() < 300) {
                String jsonBody = EntityUtils.toString(response.getEntity());

                // --- INLINE JSON PARSING ---
                // 1. Parse the JSON string into a generic Map
                Map<String, Object> jsonMap = objectMapper.readValue(jsonBody, Map.class);

                // 2. Get the "access_token" value from the Map and cast it to a String
                return (String) jsonMap.get("access_token");

            } else {
                System.err.println("Bitwarden login failed with status: " + response.getCode());
                return null;
            }
        });
    }

    // ... other methods ...
}