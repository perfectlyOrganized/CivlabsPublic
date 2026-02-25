package com.minecraftcivilizations.specialization.Config;

import com.minecraftcivilizations.specialization.OpenLab;
import com.typesafe.config.*;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Recipe;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Wildcard {
    private static final Pattern PATTERN_PLACEHOLDER = Pattern.compile("\\[(.*?)\\]", Pattern.CASE_INSENSITIVE);
        public static List<String> expandPatternInString(String input) {
            if (!input.contains("[")) {
                return Collections.singletonList(input);
            }

            OpenLab.logger.info("Expanding pattern in input: " + input);

            String namespace = "";
            String baseInput = input;
            if (input.contains(":")) {
                String[] parts = input.split(":", 2);
                namespace = parts[0] + ":";
                baseInput = parts[1];
                OpenLab.logger.info("Extracted namespace: " + namespace + " from input: " + input);
            }

            List<String> results = new ArrayList<>();
            results.add(baseInput);

            Matcher matcher = PATTERN_PLACEHOLDER.matcher(baseInput);
            while (matcher.find()) {
                String placeholder = matcher.group(1);
                OpenLab.logger.info("Found placeholder: [" + placeholder + "]");

                List<String> expansions = getExpansionsForPlaceholder(placeholder);
                OpenLab.logger.info("Got " + expansions.size() + " expansions for placeholder [" + placeholder + "]: " + expansions);

                if (expansions.isEmpty()) {
                    OpenLab.logger.warning("No expansions found for placeholder: [" + placeholder + "]");
                    continue;
                }

                List<String> newResults = new ArrayList<>();
                String prefix = baseInput.substring(0, matcher.start());
                String suffix = baseInput.substring(matcher.end());

                OpenLab.logger.info("Prefix: '" + prefix + "', Suffix: '" + suffix + "'");
                OpenLab.logger.info("Current results before expansion: " + results);

                for (String current : results) {
                    for (String expansion : expansions) {
                        newResults.add(prefix + expansion + suffix);
                    }
                }

                results = newResults;
                OpenLab.logger.info("Results after expansion: " + results);

                if (!results.isEmpty()) {
                    baseInput = results.get(0);
                    matcher = PATTERN_PLACEHOLDER.matcher(baseInput);
                } else {
                    OpenLab.logger.warning("No results after expansion, breaking loop");
                    break;
                }
            }

            if (!namespace.isEmpty()) {
                OpenLab.logger.info("Adding namespace " + namespace + " back to " + results.size() + " results");
                for (int i = 0; i < results.size(); i++) {
                    results.set(i, namespace + results.get(i));
                }
            }

            OpenLab.logger.info("Final expanded results: " + results);
            return results;
        }

        private static List<String> getExpansionsForPlaceholder(String placeholder) {
            List<String> expansions = new ArrayList<>();
            boolean isUppercase = placeholder.equals(placeholder.toUpperCase());
            switch (placeholder.toLowerCase()) {
                case "block":
                    for (Material material : Material.values()) {
                        if (material.isBlock()) expansions.add(material.name());
                    }
                    break;
                case "item":
                    for (Material material : Material.values()) {
                        if (material.isItem()) expansions.add(material.name());
                    }
                    break;
                case "entity":
                    for (EntityType entity : EntityType.values()) {
                        if (entity != EntityType.UNKNOWN) expansions.add(entity.name());
                    }
                    break;
                case "recipe":
                    Iterator<Recipe> recipeIterator = Bukkit.getServer().recipeIterator();
                    while (recipeIterator.hasNext()) {
                        Recipe recipe = recipeIterator.next();
                        if (recipe instanceof Keyed keyed) expansions.add(keyed.getKey().toString());
                    }
                    break;
            }
            if (isUppercase) {
                expansions.replaceAll(String::toUpperCase);
            } else {
                expansions.replaceAll(String::toLowerCase);
            }
            return expansions;
        }

        public static Config expandConfig(Config config) {
            if (config == null) {
                return ConfigFactory.empty();
            }

            Map<String, Object> expandedEntries = new LinkedHashMap<>();

            for (Map.Entry<String, ConfigValue> entry : config.root().entrySet()) {
                String key = entry.getKey();
                ConfigValue value = entry.getValue();

                List<String> expandedKeys = expandPatternInString(key);
                Object processedValue = processValue(value);

                if (!(processedValue instanceof String && ((String) processedValue).contains("["))) {
                    for (String expandedKey : expandedKeys) {
                        expandedEntries.put(expandedKey, processedValue);
                    }
                    continue;
                }

                List<String> expandedValues = expandPatternInString((String) processedValue);

                if (expandedKeys.size() == 1 && expandedValues.size() == 1) {
                    expandedEntries.put(expandedKeys.get(0), expandedValues.get(0));
                    continue;
                }

                if (expandedKeys.size() == 1) {
                    for (int i = 0; i < expandedValues.size(); i++) {
                        String suffix = i == 0 ? "" : "[" + i + "]";
                        expandedEntries.put(expandedKeys.get(0) + suffix, expandedValues.get(i));
                    }
                    continue;
                }

                if (expandedValues.size() == 1) {
                    for (String expandedKey : expandedKeys) {
                        expandedEntries.put(expandedKey, expandedValues.get(0));
                    }
                    continue;
                }

                for (String expandedKey : expandedKeys) {
                    for (String expandedValue : expandedValues) {
                        expandedEntries.put(expandedKey, expandedValue);
                    }
                }
            }
            return ConfigFactory.parseMap(expandedEntries);
        }

    private static Object processValue(ConfigValue value) {
        if (value.valueType() == ConfigValueType.OBJECT) {
            return expandConfig(((ConfigObject) value).toConfig());
        } else if (value.valueType() == ConfigValueType.LIST) {
            List<Object> processedList = new ArrayList<>();
            for (ConfigValue item : (ConfigList) value) {
                processedList.add(processValue(item));
            }
            return processedList;
        } else {
            return value.unwrapped();
        }
    }
}
