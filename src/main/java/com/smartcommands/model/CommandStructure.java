package com.smartcommands.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the parsed structure of a command
 *
 * Examples:
 * - "docker ps -a" -> base: docker, subcommand: ps, flags: [-a]
 * - "git commit -m 'test'" -> base: git, subcommand: commit, flags: [-m], args: ['test']
 * - "ls -la /home" -> base: ls, flags: [-la], args: [/home]
 */
public final class CommandStructure {
    private final String rawCommand;
    private final String baseCommand;
    private final String subcommand;
    private final List<String> flags;
    private final List<String> arguments;

    private CommandStructure(Builder builder) {
        this.rawCommand = builder.rawCommand;
        this.baseCommand = builder.baseCommand;
        this.subcommand = builder.subcommand;
        this.flags = Collections.unmodifiableList(new ArrayList<>(builder.flags));
        this.arguments = Collections.unmodifiableList(new ArrayList<>(builder.arguments));
    }

    public String getRawCommand() {
        return rawCommand;
    }

    public String getBaseCommand() {
        return baseCommand;
    }

    public String getSubcommand() {
        return subcommand;
    }

    public List<String> getFlags() {
        return flags;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public boolean hasSubcommand() {
        return subcommand != null && !subcommand.isEmpty();
    }

    public boolean hasFlags() {
        return !flags.isEmpty();
    }

    public boolean hasArguments() {
        return !arguments.isEmpty();
    }

    /**
     * Reconstruct the command from its parts
     */
    public String reconstruct() {
        StringBuilder sb = new StringBuilder(baseCommand);

        if (hasSubcommand()) {
            sb.append(" ").append(subcommand);
        }

        for (String flag : flags) {
            sb.append(" ").append(flag);
        }

        for (String arg : arguments) {
            // Quote arguments that contain spaces
            if (arg.contains(" ")) {
                sb.append(" '").append(arg).append("'");
            } else {
                sb.append(" ").append(arg);
            }
        }

        return sb.toString();
    }

    /**
     * Reconstruct the command with a corrected subcommand, preserving all flags and arguments
     */
    public String reconstructWithCorrectedSubcommand(String correctedSubcommand) {
        StringBuilder sb = new StringBuilder(baseCommand);

        if (correctedSubcommand != null && !correctedSubcommand.isEmpty()) {
            sb.append(" ").append(correctedSubcommand);
        }

        // Preserve all original flags
        for (String flag : flags) {
            sb.append(" ").append(flag);
        }

        // Preserve all original arguments
        for (String arg : arguments) {
            // Quote arguments that contain spaces
            if (arg.contains(" ")) {
                sb.append(" '").append(arg).append("'");
            } else {
                sb.append(" ").append(arg);
            }
        }

        return sb.toString();
    }

    /**
     * Reconstruct the command with a corrected base command, preserving all other parts
     */
    public String reconstructWithCorrectedBaseCommand(String correctedBaseCommand) {
        StringBuilder sb = new StringBuilder(correctedBaseCommand);

        if (hasSubcommand()) {
            sb.append(" ").append(subcommand);
        }

        for (String flag : flags) {
            sb.append(" ").append(flag);
        }

        for (String arg : arguments) {
            // Quote arguments that contain spaces
            if (arg.contains(" ")) {
                sb.append(" '").append(arg).append("'");
            } else {
                sb.append(" ").append(arg);
            }
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommandStructure that = (CommandStructure) o;
        return Objects.equals(baseCommand, that.baseCommand) &&
               Objects.equals(subcommand, that.subcommand) &&
               Objects.equals(flags, that.flags) &&
               Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseCommand, subcommand, flags, arguments);
    }

    @Override
    public String toString() {
        return String.format("CommandStructure{base='%s', subcommand='%s', flags=%s, args=%s}",
            baseCommand, subcommand, flags, arguments);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String rawCommand;
        private String baseCommand;
        private String subcommand;
        private final List<String> flags = new ArrayList<>();
        private final List<String> arguments = new ArrayList<>();

        public Builder rawCommand(String rawCommand) {
            this.rawCommand = rawCommand;
            return this;
        }

        public Builder baseCommand(String baseCommand) {
            this.baseCommand = baseCommand;
            return this;
        }

        public Builder subcommand(String subcommand) {
            this.subcommand = subcommand;
            return this;
        }

        public Builder addFlag(String flag) {
            this.flags.add(flag);
            return this;
        }

        public Builder addFlags(List<String> flags) {
            this.flags.addAll(flags);
            return this;
        }

        public Builder addArgument(String argument) {
            this.arguments.add(argument);
            return this;
        }

        public Builder addArguments(List<String> arguments) {
            this.arguments.addAll(arguments);
            return this;
        }

        public CommandStructure build() {
            Objects.requireNonNull(rawCommand, "rawCommand must not be null");
            Objects.requireNonNull(baseCommand, "baseCommand must not be null");
            return new CommandStructure(this);
        }
    }
}