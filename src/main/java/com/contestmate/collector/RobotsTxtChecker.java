package com.contestmate.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal robots.txt evaluator: honours `Disallow` rules under `User-agent: *`.
 * Fails closed (treats the source as disallowed) if robots.txt cannot be read at all,
 * except for a network error where we simply cannot confirm either way.
 */
@Component
public class RobotsTxtChecker {

    private static final Logger log = LoggerFactory.getLogger(RobotsTxtChecker.class);
    private static final String USER_AGENT = "MatchUpContestBot/1.0 (+contest recommendation research)";

    public boolean isAllowed(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            URL robotsUrl = new URI(uri.getScheme(), uri.getAuthority(), "/robots.txt", null, null).toURL();
            HttpURLConnection connection = (HttpURLConnection) robotsUrl.openConnection();
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int status = connection.getResponseCode();
            if (status == 404) {
                return true; // no robots.txt => allowed by default
            }
            if (status >= 400) {
                log.warn("robots.txt fetch returned {} for {}", status, baseUrl);
                return false;
            }
            List<String> disallowRules = new ArrayList<>();
            boolean inWildcardSection = false;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String lower = line.toLowerCase();
                    if (lower.startsWith("user-agent:")) {
                        inWildcardSection = line.substring(11).trim().equals("*");
                    } else if (inWildcardSection && lower.startsWith("disallow:")) {
                        String path = line.substring(9).trim();
                        if (!path.isEmpty()) disallowRules.add(path);
                    }
                }
            }
            String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
            return disallowRules.stream().noneMatch(path::startsWith);
        } catch (Exception e) {
            log.warn("Could not evaluate robots.txt for {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }
}
