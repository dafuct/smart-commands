package com.smartcommands.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Collections;
import java.util.Set;

@Getter
@EqualsAndHashCode
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

    public boolean isValidSubcommand(String subcommand) {
        return validSubcommands.contains(subcommand.toLowerCase());
    }

    public boolean isValidFlag(String flag) {
        String normalizedFlag = flag.replaceFirst("^-+", "");
        return validFlags.stream()
            .anyMatch(validFlag -> validFlag.replaceFirst("^-+", "").equals(normalizedFlag));
    }

    public boolean hasSubcommands() {
        return !validSubcommands.isEmpty();
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
            return new CommandMetadata(this);
        }
    }
}
