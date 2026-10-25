package ru.ashemchuk;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class CommandLine {
    private CommandLine() {}

    static Map<String, String> parse(String[] args, Set<String> values, Set<String> flags) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            String value;
            if (flags.contains(option)) value = "true";
            else if (values.contains(option) && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            } else throw new IllegalArgumentException("Unknown option or missing value: " + option);
            if (options.putIfAbsent(option, value) != null) {
                throw new IllegalArgumentException("Duplicate option: " + option);
            }
        }
        return options;
    }
}
