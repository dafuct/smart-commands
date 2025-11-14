package com.smartcommands.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the parsed structure of a command
 * Examples:
 * - "docker ps -a" -> base: docker, subcommand: ps, flags: [-a]
 * - "git commit -m 'test'" -> base: git, subcommand: commit, flags: [-m], args: ['test']
 * - "ls -la /home" -> base: ls, flags: [-la], args: [/home]
 */
@Getter
@EqualsAndHashCode
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
        this.flags = List.copyOf(builder.flags);
        this.arguments = List.copyOf(builder.arguments);
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

    public String reconstruct() {
        return getString(baseCommand);
    }

    private String getString(String baseCommand) {
        StringBuilder sb = new StringBuilder(baseCommand);

        if (hasSubcommand()) {
            sb.append(" ").append(subcommand);
        }

        return getString(sb);
    }

    private String getString(StringBuilder sb) {
        for (String flag : flags) {
            sb.append(" ").append(flag);
        }

        for (String arg : arguments) {
            if (arg.contains(" ")) {
                sb.append(" '").append(arg).append("'");
            } else {
                sb.append(" ").append(arg);
            }
        }

        return sb.toString();
    }

    public String reconstructWithCorrectedSubcommand(String correctedSubcommand) {
        StringBuilder sb = new StringBuilder(baseCommand);

        if (correctedSubcommand != null && !correctedSubcommand.isEmpty()) {
            sb.append(" ").append(correctedSubcommand);
        }

        return getString(sb);
    }

    public String reconstructWithCorrectedBaseCommand(String correctedBaseCommand) {
        return getString(correctedBaseCommand);
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
            return new CommandStructure(this);
        }
    }
}