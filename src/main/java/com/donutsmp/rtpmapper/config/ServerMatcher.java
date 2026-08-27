package com.donutsmp.rtpmapper.config;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Normalizes Minecraft server addresses and matches them against exact hosts or
 * leading wildcard patterns such as {@code *.donutsmp.net}.
 */
public final class ServerMatcher {
    private ServerMatcher() {
    }

    public static boolean matches(String address, Collection<String> patterns) {
        if (address == null || patterns == null || patterns.isEmpty()) {
            return false;
        }

        final String host;
        try {
            host = normalizeHost(address);
        } catch (IllegalArgumentException ignored) {
            return false;
        }

        for (String pattern : patterns) {
            try {
                if (matchesNormalized(host, normalizePattern(pattern))) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // A malformed user-provided pattern must never enable a server.
            }
        }
        return false;
    }

    public static boolean matchesPattern(String address, String pattern) {
        return matches(address, List.of(pattern));
    }

    public static List<String> normalizePatterns(Collection<String> patterns) {
        Objects.requireNonNull(patterns, "patterns");
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed server pattern is required");
        }

        List<String> normalized = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            String value = normalizePattern(pattern);
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed server pattern is required");
        }
        return List.copyOf(normalized);
    }

    /** Normalizes an allowlist where an empty list intentionally denies every server. */
    public static List<String> normalizeOptionalPatterns(Collection<String> patterns) {
        Objects.requireNonNull(patterns, "patterns");
        List<String> normalized = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            String value = normalizePattern(pattern);
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    public static String normalizePattern(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("Server pattern cannot be null");
        }

        String candidate = pattern.trim().toLowerCase(Locale.ROOT);
        boolean wildcard = candidate.startsWith("*.");
        String hostPart = wildcard ? candidate.substring(2) : candidate;
        if (hostPart.indexOf('*') >= 0) {
            throw new IllegalArgumentException("Only a leading '*.' wildcard is supported: " + pattern);
        }

        String host = normalizeHost(hostPart);
        if (isIpLiteral(host) && wildcard) {
            throw new IllegalArgumentException("Wildcard IP patterns are not supported: " + pattern);
        }
        return wildcard ? "*." + host : host;
    }

    /**
     * Removes a numeric port, IPv6 brackets, a trailing DNS dot, and applies
     * case/IDN normalization.
     */
    public static String normalizeHost(String address) {
        if (address == null) {
            throw new IllegalArgumentException("Server address cannot be null");
        }

        String candidate = address.trim();
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("Server address cannot be blank");
        }

        if (candidate.contains("://")) {
            try {
                URI uri = new URI(candidate);
                if (uri.getHost() == null) {
                    throw new IllegalArgumentException("Invalid server address: " + address);
                }
                candidate = uri.getHost();
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("Invalid server address: " + address, exception);
            }
        } else if (candidate.startsWith("[")) {
            int closingBracket = candidate.indexOf(']');
            if (closingBracket < 0) {
                throw new IllegalArgumentException("Invalid bracketed server address: " + address);
            }
            String suffix = candidate.substring(closingBracket + 1);
            if (!suffix.isEmpty() && !isNumericPortSuffix(suffix)) {
                throw new IllegalArgumentException("Invalid server port: " + address);
            }
            candidate = candidate.substring(1, closingBracket);
        } else {
            int firstColon = candidate.indexOf(':');
            int lastColon = candidate.lastIndexOf(':');
            if (firstColon >= 0 && firstColon == lastColon) {
                String suffix = candidate.substring(firstColon);
                if (!isNumericPortSuffix(suffix)) {
                    throw new IllegalArgumentException("Invalid server port: " + address);
                }
                candidate = candidate.substring(0, firstColon);
            }
        }

        candidate = candidate.trim().toLowerCase(Locale.ROOT);
        while (candidate.endsWith(".")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (candidate.isEmpty() || candidate.indexOf('/') >= 0 || candidate.indexOf('\\') >= 0
                || candidate.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Invalid server host: " + address);
        }

        if (candidate.indexOf(':') >= 0) {
            // A bare IPv6 literal. IDN normalization is not applicable.
            return candidate;
        }

        try {
            String ascii = IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            while (ascii.endsWith(".")) {
                ascii = ascii.substring(0, ascii.length() - 1);
            }
            if (ascii.isEmpty() || ascii.length() > 253) {
                throw new IllegalArgumentException("Invalid server host: " + address);
            }
            return ascii;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid server host: " + address, exception);
        }
    }

    private static boolean matchesNormalized(String host, String pattern) {
        if (!pattern.startsWith("*.")) {
            return host.equals(pattern);
        }
        String suffix = pattern.substring(2);
        return host.length() > suffix.length() + 1 && host.endsWith("." + suffix);
    }

    private static boolean isNumericPortSuffix(String suffix) {
        if (suffix.length() < 2 || suffix.charAt(0) != ':') {
            return false;
        }
        try {
            int port = Integer.parseInt(suffix.substring(1));
            return port >= 1 && port <= 65_535;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return true;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }
}
