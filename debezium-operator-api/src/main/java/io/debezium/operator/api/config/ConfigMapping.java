/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.operator.api.config;

import static java.util.function.Predicate.not;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Convenience wrapper used to build properties-like configuration from arbitrary map
 */
public final class ConfigMapping {

    public enum KeyType {
        RELATIVE,
        ABSOLUTE;
    }

    public record Key(String name, KeyType type) {
        public static Key rel(String key) {
            return new Key(key, KeyType.RELATIVE);
        }

        public static Key abs(String key) {
            return new Key(key, KeyType.ABSOLUTE);
        }

        public static Key root() {
            return new Key(null, null);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final Map<Key, String> config;
    private final String prefix;

    public static ConfigMapping from(Map<String, ?> properties) {
        var config = ConfigMapping.empty();
        config.putAll(properties);
        return config;
    }

    public static ConfigMapping empty() {
        return new ConfigMapping(null);
    }

    public static ConfigMapping prefixed(String prefix) {
        return new ConfigMapping(prefix);
    }

    public ConfigMapping(String prefix) {
        this.config = new HashMap<>();
        this.prefix = prefix;
    }

    public Map<String, String> getAsMapSimple() {
        return config.entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
    }

    public Map<Key, String> getAsMap() {
        return config;
    }

    public String getAsString() {
        return config.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .sorted()
                .collect(Collectors.joining(System.lineSeparator()));
    }

    public ConfigMapping rootValue(Object value) {
        putInternal(value, Key.root());
        return this;
    }

    public ConfigMapping put(String key, Object value) {
        putInternal(value, Key.rel(key));
        return this;
    }

    public ConfigMapping putAbs(String key, Object value) {
        putInternal(value, Key.abs(key));
        return this;
    }

    public ConfigMapping putAll(ConfigMappable resource) {
        putAll(resource.asConfiguration());
        return this;
    }

    public ConfigMapping putAll(String key, ConfigMappable resource) {
        putAll(key, resource.asConfiguration());
        return this;
    }

    public ConfigMapping putAll(ConfigMapping config) {
        config.getAsMap().forEach((key, value) -> putInternal(value, key));
        return this;
    }

    public ConfigMapping putAll(String key, ConfigMapping config) {
        config.getAsMap().forEach((subKey, value) -> putInternal(value, key, subKey));
        return this;
    }

    public ConfigMapping putAll(Map<String, ?> props) {
        props.forEach((key, value) -> putInternal(value, Key.rel(key)));
        return this;
    }

    public <T extends ConfigMappable> ConfigMapping putList(String key, List<T> items, String name) {
        if (items.isEmpty()) {
            return this;
        }

        record NamedItem(String name, ConfigMappable item) {
        }

        var named = IntStream.
                range(0, items.size())
                .mapToObj(i -> new NamedItem(name + i, items.get(i)))
                .toList();

        named.stream()
                .map(NamedItem::name)
                .reduce((x, y) -> String.join(","))
                .ifPresent(names -> put(key, names));


        named.forEach(item -> putAll(key + "." + item.name, item.item));
        return this;
    }

    public <T extends ConfigMappable> ConfigMapping putMap(String key, Map<String, T> items) {
        items.keySet().stream()
                .reduce((x, y) -> String.join(","))
                .ifPresent(names -> put(key, names));

        items.forEach((name, item) -> putAll(key + "." + name, item));
        return this;
    }

    private void putInternal(Object value, Key key) {
        if (value == null) {
            return;
        }
        var combined = prefix(null, key);
        config.put(combined, String.valueOf(value));
    }

    private void putInternal(Object value, String key, Key subKey) {
        if (value == null) {
            return;
        }
        var combined = prefix(key, subKey);
        config.put(combined, String.valueOf(value));
    }

    private Key prefix(Key key) {
        if (key.type == KeyType.ABSOLUTE) {
            return key;
        }

        var combined = Stream.of(prefix, key.name)
                .filter(Objects::nonNull)
                .filter(not(String::isBlank))
                .collect(Collectors.joining("."));

        return Key.rel(combined);
    }

    private Key prefix(String key, Key subKey) {
        if (subKey.type == KeyType.ABSOLUTE) {
            return subKey;
        }

        var combined = Stream.concat(Stream.of(prefix), Stream.of(key, subKey.name))
                .filter(Objects::nonNull)
                .filter(not(String::isBlank))
                .collect(Collectors.joining("."));

        return Key.rel(combined);
    }

    public String md5Sum() {
        byte[] digest = new byte[0];
        try {
            var md = MessageDigest.getInstance("MD5");
            digest = md.digest(getAsString().getBytes());
        }
        catch (NoSuchAlgorithmException e) {
            // This will never happen
        }
        return toHex(digest);
    }

    private String toHex(byte[] bytes) {
        var hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Override
    public String toString() {
        return config.toString();
    }
}
