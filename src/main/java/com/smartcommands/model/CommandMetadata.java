package com.smartcommands.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata about a command including valid subcommands and flags
 */
public final class CommandMetadata {
    private final String baseCommand;
    private final Set<String> validSubcommands;
    private final Set<String> validFlags;
    private final Set<String> validFlagsForSubcommand;
    private final String description;

    private CommandMetadata(Builder builder) {
        this.baseCommand = builder.baseCommand;
        this.validSubcommands = builder.validSubcommands != null
            ? Set.copyOf(builder.validSubcommands)
            : Collections.emptySet();
        this.validFlags = builder.validFlags != null
            ? Set.copyOf(builder.validFlags)
            : Collections.emptySet();
        this.validFlagsForSubcommand = builder.validFlagsForSubcommand != null
            ? Set.copyOf(builder.validFlagsForSubcommand)
            : Collections.emptySet();
        this.description = builder.description;
    }

    public String getBaseCommand() {
        return baseCommand;
    }

    public Set<String> getValidSubcommands() {
        return validSubcommands;
    }

    public Set<String> getValidFlags() {
        return validFlags;
    }

    public Set<String> getValidFlagsForSubcommand() {
        return validFlagsForSubcommand;
    }

    public String getDescription() {
        return description;
    }

    public boolean isValidSubcommand(String subcommand) {
        return validSubcommands.contains(subcommand.toLowerCase());
    }

    public boolean isValidFlag(String flag) {
        // Normalize flag (remove leading dashes for comparison)
        String normalizedFlag = flag.replaceFirst("^-+", "");
        return validFlags.stream()
            .anyMatch(validFlag -> validFlag.replaceFirst("^-+", "").equals(normalizedFlag));
    }

    public boolean hasSubcommands() {
        return !validSubcommands.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommandMetadata that = (CommandMetadata) o;
        return Objects.equals(baseCommand, that.baseCommand);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseCommand);
    }

    @Override
    public String toString() {
        return String.format("CommandMetadata{command='%s', subcommands=%d, flags=%d}",
            baseCommand, validSubcommands.size(), validFlags.size());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseCommand;
        private Set<String> validSubcommands;
        private Set<String> validFlags;
        private Set<String> validFlagsForSubcommand;
        private String description;

        public Builder baseCommand(String baseCommand) {
            this.baseCommand = baseCommand;
            return this;
        }

        public Builder validSubcommands(Set<String> validSubcommands) {
            this.validSubcommands = validSubcommands;
            return this;
        }

        public Builder validFlags(Set<String> validFlags) {
            this.validFlags = validFlags;
            return this;
        }

        public Builder validFlagsForSubcommand(Set<String> validFlagsForSubcommand) {
            this.validFlagsForSubcommand = validFlagsForSubcommand;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public CommandMetadata build() {
            Objects.requireNonNull(baseCommand, "baseCommand must not be null");
            return new CommandMetadata(this);
        }
    }
}
