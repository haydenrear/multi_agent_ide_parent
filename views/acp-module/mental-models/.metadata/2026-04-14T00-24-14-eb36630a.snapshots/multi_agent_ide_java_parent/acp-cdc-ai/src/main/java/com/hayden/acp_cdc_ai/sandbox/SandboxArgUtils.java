package com.hayden.acp_cdc_ai.sandbox;

import java.util.List;

/**
 * Utility methods for checking and manipulating CLI arguments in sandbox strategies.
 * 
 * <p>These utilities help determine whether specific flags or flag-value pairs
 * already exist in a list of arguments, supporting both:</p>
 * <ul>
 *   <li>Boolean flags (e.g., {@code --verbose})</li>
 *   <li>Flag-value pairs (e.g., {@code --sandbox workspace-write})</li>
 *   <li>Short and long flag variants (e.g., {@code -C} and {@code --cd})</li>
 * </ul>
 */
public final class SandboxArgUtils {

    private SandboxArgUtils() {
        // Utility class
    }

    /**
     * Checks if any of the given flags exist in the args list.
     * Use this for boolean flags that don't take a value.
     * 
     * @param args the list of arguments to search
     * @param flags the flag names to look for (e.g., "--verbose", "-v")
     * @return true if any flag is present
     */
    public static boolean hasFlag(List<String> args, String... flags) {
        if (args == null || args.isEmpty()) {
            return false;
        }
        for (String flag : flags) {
            if (args.contains(flag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a flag exists in the args list (regardless of its value).
     * Use this when you only care that a flag is set, not what value it has.
     * 
     * @param args the list of arguments to search
     * @param flags the flag names to look for (e.g., "--permission-mode", "-p")
     * @return true if any flag is present
     */
    public static boolean hasFlagWithAnyValue(List<String> args, String... flags) {
        return hasFlag(args, flags);
    }

    /**
     * Checks if a flag-value pair exists sequentially in the args list.
     * The flag must be immediately followed by the specified value.
     * 
     * <p>Example: For args {@code ["--add-dir", "/path/a", "--add-dir", "/path/b"]},
     * calling {@code hasFlagValuePair(args, "/path/a", "--add-dir")} returns true.</p>
     * 
     * @param args the list of arguments to search
     * @param value the value that should follow the flag
     * @param flags the flag names to look for (e.g., "--add-dir", "-d")
     * @return true if any flag is immediately followed by the specified value
     */
    public static boolean hasFlagValuePair(List<String> args, String value, String... flags) {
        if (args == null || args.size() < 2 || value == null) {
            return false;
        }
        for (int i = 0; i < args.size() - 1; i++) {
            String current = args.get(i);
            String next = args.get(i + 1);
            for (String flag : flags) {
                if (flag.equals(current) && value.equals(next)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the value following a flag in the args list.
     * Returns null if the flag is not found or has no following value.
     * 
     * @param args the list of arguments to search
     * @param flags the flag names to look for
     * @return the value following the first matching flag, or null
     */
    public static String getFlagValue(List<String> args, String... flags) {
        if (args == null || args.size() < 2) {
            return null;
        }
        for (int i = 0; i < args.size() - 1; i++) {
            String current = args.get(i);
            for (String flag : flags) {
                if (flag.equals(current)) {
                    return args.get(i + 1);
                }
            }
        }
        return null;
    }

    /**
     * Checks if a value exists anywhere in the args list.
     * Use this for checking if a path or value is already specified
     * (regardless of which flag it's associated with).
     * 
     * @param args the list of arguments to search
     * @param value the value to look for
     * @return true if the value is present
     */
    public static boolean containsValue(List<String> args, String value) {
        return args != null && value != null && args.contains(value);
    }
}
