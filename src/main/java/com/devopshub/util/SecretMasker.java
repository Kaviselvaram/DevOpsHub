package com.devopshub.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Secret Redaction & Log Masker.
 * Automatically redacts API keys, bearer tokens, passwords, private keys,
 * and authenticated URL credentials from console logs and execution histories.
 */
public class SecretMasker {

    private static final Pattern URL_CREDENTIAL_PATTERN =
            Pattern.compile("https?://([^:/\\s]+):([^@/\\s]+)@", Pattern.CASE_INSENSITIVE);

    private static final Pattern TOKEN_KEY_PATTERN =
            Pattern.compile("(?i)(password|secret|token|apikey|api_key|access_key|auth_token|authorization|bearer)(\\s*[:=]\\s*|\\s+)([\"']?)([^\\s\"',;]+)([\"']?)");

    private static final Pattern PRIVATE_KEY_PATTERN =
            Pattern.compile("-----BEGIN [A-Z ]+ PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]+ PRIVATE KEY-----");

    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;

        // 1. Redact Private Keys
        result = PRIVATE_KEY_PATTERN.matcher(result).replaceAll("[REDACTED PRIVATE KEY]");

        // 2. Redact URL Basic Auth (e.g., https://username:password@github.com -> https://username:***@github.com)
        Matcher urlMatcher = URL_CREDENTIAL_PATTERN.matcher(result);
        StringBuffer sbUrl = new StringBuffer();
        while (urlMatcher.find()) {
            urlMatcher.appendReplacement(sbUrl, "https://$1:***@");
        }
        urlMatcher.appendTail(sbUrl);
        result = sbUrl.toString();

        // 3. Redact Sensitive key-value pairs (e.g., password=secret -> password=***)
        Matcher keyMatcher = TOKEN_KEY_PATTERN.matcher(result);
        StringBuffer sbKey = new StringBuffer();
        while (keyMatcher.find()) {
            String key = keyMatcher.group(1);
            String sep = keyMatcher.group(2);
            String openQuote = keyMatcher.group(3) != null ? keyMatcher.group(3) : "";
            String closeQuote = keyMatcher.group(5) != null ? keyMatcher.group(5) : "";
            keyMatcher.appendReplacement(sbKey, Matcher.quoteReplacement(key + sep + openQuote + "***" + closeQuote));
        }
        keyMatcher.appendTail(sbKey);
        result = sbKey.toString();

        return result;
    }
}
