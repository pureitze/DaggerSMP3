package com.example.daggers;

/**
 * The eight physical actions Bukkit can actually detect as a distinct player
 * input: 4 actions (right-click, left-click, swap-hands, drop-item) each with
 * or without sneaking held. Players choose one of these for their Tier 1 and
 * one for their Tier 2 ability via /dagger set_ability.
 */
public enum AbilityTrigger {
    SHIFT_RIGHT_CLICK(InputAction.RIGHT_CLICK, true, "shift+right_click"),
    RIGHT_CLICK(InputAction.RIGHT_CLICK, false, "right_click"),
    SHIFT_LEFT_CLICK(InputAction.LEFT_CLICK, true, "shift+left_click"),
    LEFT_CLICK(InputAction.LEFT_CLICK, false, "left_click"),
    SHIFT_SWAP_HANDS(InputAction.SWAP_HANDS, true, "shift+swap_hands"),
    SWAP_HANDS(InputAction.SWAP_HANDS, false, "swap_hands"),
    SHIFT_DROP(InputAction.DROP, true, "shift+drop"),
    DROP(InputAction.DROP, false, "drop");

    public enum InputAction { RIGHT_CLICK, LEFT_CLICK, SWAP_HANDS, DROP }

    private final InputAction action;
    private final boolean requiresSneak;
    private final String displayName;

    AbilityTrigger(InputAction action, boolean requiresSneak, String displayName) {
        this.action = action;
        this.requiresSneak = requiresSneak;
        this.displayName = displayName;
    }

    public boolean matches(InputAction action, boolean sneaking) {
        return this.action == action && this.requiresSneak == sneaking;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Accepts flexible input like "shift+right_click", "Shift Right Click", "rightclick", etc. */
    public static AbilityTrigger parse(String input) {
        String normalized = input.toLowerCase().replaceAll("[^a-z]", "_");
        boolean shift = normalized.contains("shift") || normalized.contains("sneak");

        InputAction action;
        if (normalized.contains("right")) {
            action = InputAction.RIGHT_CLICK;
        } else if (normalized.contains("left")) {
            action = InputAction.LEFT_CLICK;
        } else if (normalized.contains("swap") || normalized.contains("off")) {
            action = InputAction.SWAP_HANDS;
        } else if (normalized.contains("drop") || normalized.equals("q")) {
            action = InputAction.DROP;
        } else {
            return null;
        }

        for (AbilityTrigger trigger : values()) {
            if (trigger.action == action && trigger.requiresSneak == shift) {
                return trigger;
            }
        }
        return null;
    }
}
