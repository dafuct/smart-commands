package com.smartcommands.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.smartcommands.model.CommandSuggestion;

/**
 * Fallback command validator using pattern matching and command knowledge base
 * Used when Ollama is unavailable or returns invalid responses
 */
@Component
public class FallbackCommandValidator {
    private static final Logger logger = LoggerFactory.getLogger(FallbackCommandValidator.class);

    // Command knowledge base
    private static final Map<String, CommandKnowledge> COMMAND_DATABASE = new HashMap<>();

    static {
        // Docker commands
        Set<String> dockerSubcommands = new HashSet<>();
        dockerSubcommands.add("ps");
        dockerSubcommands.add("run");
        dockerSubcommands.add("stop");
        dockerSubcommands.add("start");
        dockerSubcommands.add("restart");
        dockerSubcommands.add("rm");
        dockerSubcommands.add("rmi");
        dockerSubcommands.add("images");
        dockerSubcommands.add("exec");
        dockerSubcommands.add("logs");
        dockerSubcommands.add("build");
        dockerSubcommands.add("pull");
        dockerSubcommands.add("push");
        dockerSubcommands.add("tag");
        dockerSubcommands.add("inspect");
        dockerSubcommands.add("network");
        dockerSubcommands.add("volume");
        dockerSubcommands.add("compose");

        Map<String, Set<String>> dockerFlags = new HashMap<>();
        dockerFlags.put("ps", Set.of("-a", "--all", "-q", "--quiet", "-s", "--size", "-f", "--filter", "-n", "--last"));
        dockerFlags.put("run", Set.of("-d", "--detach", "-p", "--publish", "-v", "--volume", "-e", "--env", "--name", "-it", "--rm"));
        dockerFlags.put("logs", Set.of("-f", "--follow", "-t", "--timestamps", "--tail", "--since"));
        dockerFlags.put("images", Set.of("-a", "--all", "-q", "--quiet", "-f", "--filter"));
        dockerFlags.put("stop", Set.of("-t", "--time"));
        dockerFlags.put("rm", Set.of("-f", "--force", "-v", "--volumes"));

        Map<String, String> dockerTypos = new HashMap<>();
        dockerTypos.put("sp", "ps");
        dockerTypos.put("pss", "ps");
        dockerTypos.put("psw", "ps");
        dockerTypos.put("stat", "stats");
        dockerTypos.put("strat", "start");
        dockerTypos.put("iamges", "images");
        dockerTypos.put("imges", "images");
        dockerTypos.put("exce", "exec");

        COMMAND_DATABASE.put("docker", new CommandKnowledge(dockerSubcommands, dockerFlags, dockerTypos));

        // Git commands
        Set<String> gitSubcommands = new HashSet<>();
        gitSubcommands.add("status");
        gitSubcommands.add("add");
        gitSubcommands.add("commit");
        gitSubcommands.add("push");
        gitSubcommands.add("pull");
        gitSubcommands.add("clone");
        gitSubcommands.add("checkout");
        gitSubcommands.add("branch");
        gitSubcommands.add("merge");
        gitSubcommands.add("log");
        gitSubcommands.add("diff");
        gitSubcommands.add("reset");
        gitSubcommands.add("rebase");
        gitSubcommands.add("fetch");
        gitSubcommands.add("remote");
        gitSubcommands.add("tag");
        gitSubcommands.add("stash");

        Map<String, Set<String>> gitFlags = new HashMap<>();
        gitFlags.put("status", Set.of("-s", "--short", "-b", "--branch", "-u", "--untracked-files"));
        gitFlags.put("add", Set.of("-A", "--all", "-p", "--patch", "-u", "--update"));
        gitFlags.put("commit", Set.of("-m", "--message", "-a", "--all", "--amend", "-v", "--verbose"));
        gitFlags.put("push", Set.of("-u", "--set-upstream", "-f", "--force", "--all", "--tags"));
        gitFlags.put("log", Set.of("--oneline", "--graph", "--all", "-p", "--patch", "-n", "--max-count"));

        Map<String, String> gitTypos = new HashMap<>();
        gitTypos.put("stauts", "status");
        gitTypos.put("statsu", "status");
        gitTypos.put("staus", "status");
        gitTypos.put("comit", "commit");
        gitTypos.put("commti", "commit");
        gitTypos.put("pussh", "push");
        gitTypos.put("chekout", "checkout");
        gitTypos.put("checkotu", "checkout");

        COMMAND_DATABASE.put("git", new CommandKnowledge(gitSubcommands, gitFlags, gitTypos));

        // kubectl commands
        Set<String> kubectlSubcommands = new HashSet<>();
        kubectlSubcommands.add("get");
        kubectlSubcommands.add("describe");
        kubectlSubcommands.add("logs");
        kubectlSubcommands.add("exec");
        kubectlSubcommands.add("apply");
        kubectlSubcommands.add("delete");
        kubectlSubcommands.add("create");
        kubectlSubcommands.add("scale");
        kubectlSubcommands.add("rollout");
        kubectlSubcommands.add("port-forward");

        Map<String, Set<String>> kubectlFlags = new HashMap<>();
        kubectlFlags.put("get", Set.of("-o", "--output", "-w", "--watch", "-n", "--namespace", "--all-namespaces"));
        kubectlFlags.put("logs", Set.of("-f", "--follow", "-c", "--container", "--tail", "--since"));

        COMMAND_DATABASE.put("kubectl", new CommandKnowledge(kubectlSubcommands, kubectlFlags, new HashMap<>()));
    }

    /**
     * Validate command using pattern matching and command knowledge
     */
    public CommandSuggestion validate(String command) {
        logger.debug("Fallback validation for: {}", command);

        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
            return CommandSuggestion.error("Command cannot be empty");
        }

        String[] parts = trimmed.split("\\s+");
        String baseCommand = parts[0];

        // Check for common base command typos
        String correctedBase = checkBaseCommandTypo(baseCommand);
        if (correctedBase != null) {
            String correctedCommand = trimmed.replaceFirst(Pattern.quote(baseCommand), correctedBase);
            return CommandSuggestion.correction(command, correctedCommand);
        }

        // Check if we have knowledge about this command
        CommandKnowledge knowledge = COMMAND_DATABASE.get(baseCommand);
        if (knowledge != null) {
            return validateWithKnowledge(command, parts, knowledge);
        }

        // Check if it's a common standalone command
        if (isCommonCommand(baseCommand)) {
            return CommandSuggestion.regularCommand(command);
        }

        // Unknown command
        return CommandSuggestion.error(
            "Unknown command: '" + baseCommand + "'. Type 'sc \"describe what you want to do\"' for help."
        );
    }

    /**
     * Validate command using knowledge base
     */
    private CommandSuggestion validateWithKnowledge(String originalCommand, String[] parts, CommandKnowledge knowledge) {
        if (parts.length < 2) {
            // Base command without subcommand - valid for some commands
            return CommandSuggestion.regularCommand(originalCommand);
        }

        String baseCommand = parts[0];
        String subcommand = parts[1];

        // Check if subcommand is a flag (starts with -)
        if (subcommand.startsWith("-")) {
            // Some commands like 'docker -v' are valid
            return CommandSuggestion.regularCommand(originalCommand);
        }

        // Check for subcommand typo
        String correctedSubcommand = knowledge.correctSubcommandTypo(subcommand);
        if (correctedSubcommand != null) {
            String correctedCommand = originalCommand.replaceFirst(
                Pattern.quote(baseCommand + " " + subcommand),
                baseCommand + " " + correctedSubcommand
            );
            return CommandSuggestion.correction(originalCommand, correctedCommand);
        }

        // Check if subcommand is valid
        if (!knowledge.isValidSubcommand(subcommand)) {
            String suggestion = knowledge.findSimilarSubcommand(subcommand);
            if (suggestion != null) {
                String correctedCommand = originalCommand.replaceFirst(
                    Pattern.quote(baseCommand + " " + subcommand),
                    baseCommand + " " + suggestion
                );
                return CommandSuggestion.correction(originalCommand, correctedCommand);
            }

            return CommandSuggestion.error(
                "Invalid subcommand: '" + subcommand + "' for '" + baseCommand + "'. " +
                "Type 'sc \"" + baseCommand + " help\"' to see available options."
            );
        }

        // Validate flags if present
        if (parts.length > 2) {
            String invalidFlag = validateFlags(parts, 2, knowledge, subcommand);
            if (invalidFlag != null) {
                // Try to suggest a correction
                String suggestedFlag = knowledge.findSimilarFlag(subcommand, invalidFlag);
                if (suggestedFlag != null) {
                    String correctedCommand = originalCommand.replace(invalidFlag, suggestedFlag);
                    return CommandSuggestion.correction(originalCommand, correctedCommand);
                }
            }
        }

        // Command is valid
        return CommandSuggestion.regularCommand(originalCommand);
    }

    /**
     * Validate flags in command
     */
    private String validateFlags(String[] parts, int startIndex, CommandKnowledge knowledge, String subcommand) {
        for (int i = startIndex; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("-")) {
                // It's a flag - check if it's valid
                if (!knowledge.isValidFlag(subcommand, part) && !isCommonFlag(part)) {
                    return part;
                }
            }
        }
        return null;
    }

    /**
     * Check for common flag patterns that are generally valid
     */
    private boolean isCommonFlag(String flag) {
        // Common flags that work across many commands
        return flag.equals("-h") || flag.equals("--help") ||
               flag.equals("-v") || flag.equals("--version") ||
               flag.equals("-V") || flag.equals("--verbose");
    }

    /**
     * Check for common base command typos
     */
    private String checkBaseCommandTypo(String command) {
        Map<String, String> commonTypos = new HashMap<>();
        commonTypos.put("gti", "git");
        commonTypos.put("igt", "git");
        commonTypos.put("got", "git");
        commonTypos.put("dokcer", "docker");
        commonTypos.put("dcoker", "docker");
        commonTypos.put("docekr", "docker");
        commonTypos.put("lss", "ls");
        commonTypos.put("lsl", "ls");
        commonTypos.put("sl", "ls");
        commonTypos.put("mdkir", "mkdir");
        commonTypos.put("mkidr", "mkdir");
        commonTypos.put("grpe", "grep");
        commonTypos.put("gerp", "grep");
        commonTypos.put("chmdo", "chmod");
        commonTypos.put("chmdo", "chmod");

        return commonTypos.get(command);
    }

    /**
     * Check if command is a common standalone command
     */
    private boolean isCommonCommand(String command) {
        String[] commonCommands = {
            "ls", "cd", "pwd", "mkdir", "rm", "cp", "mv", "cat", "grep",
            "find", "chmod", "chown", "ps", "kill", "top", "df", "du",
            "tar", "gzip", "ssh", "scp", "wget", "curl", "npm",
            "pip", "java", "python", "node", "mvn",
            "gradle", "echo", "date", "whoami", "id", "uname", "which",
            "vim", "nano", "emacs", "less", "more", "head", "tail",
            "sort", "uniq", "wc", "diff", "sed", "awk", "make",
            "systemctl", "service", "journalctl", "dmesg"
        };

        for (String commonCommand : commonCommands) {
            if (commonCommand.equals(command)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inner class to hold command knowledge
     */
    private static class CommandKnowledge {
        private final Set<String> validSubcommands;
        private final Map<String, Set<String>> validFlags;
        private final Map<String, String> subcommandTypos;

        public CommandKnowledge(Set<String> validSubcommands,
                               Map<String, Set<String>> validFlags,
                               Map<String, String> subcommandTypos) {
            this.validSubcommands = validSubcommands;
            this.validFlags = validFlags;
            this.subcommandTypos = subcommandTypos;
        }

        public boolean isValidSubcommand(String subcommand) {
            return validSubcommands.contains(subcommand);
        }

        public String correctSubcommandTypo(String typo) {
            return subcommandTypos.get(typo);
        }

        public String findSimilarSubcommand(String input) {
            // Use Levenshtein distance to find similar commands
            int minDistance = Integer.MAX_VALUE;
            String bestMatch = null;

            for (String validCmd : validSubcommands) {
                int distance = levenshteinDistance(input, validCmd);
                if (distance < minDistance && distance <= 2) {
                    minDistance = distance;
                    bestMatch = validCmd;
                }
            }

            return bestMatch;
        }

        public boolean isValidFlag(String subcommand, String flag) {
            Set<String> flags = validFlags.get(subcommand);
            if (flags == null) {
                // No specific flags defined for this subcommand, assume valid
                return true;
            }

            // Check both the flag and its long form
            String cleanFlag = flag.split("=")[0]; // Handle --flag=value
            return flags.contains(cleanFlag);
        }

        public String findSimilarFlag(String subcommand, String input) {
            Set<String> flags = validFlags.get(subcommand);
            if (flags == null) {
                return null;
            }

            int minDistance = Integer.MAX_VALUE;
            String bestMatch = null;

            for (String validFlag : flags) {
                int distance = levenshteinDistance(input, validFlag);
                if (distance < minDistance && distance <= 2) {
                    minDistance = distance;
                    bestMatch = validFlag;
                }
            }

            return bestMatch;
        }

        /**
         * Calculate Levenshtein distance between two strings
         */
        private int levenshteinDistance(String s1, String s2) {
            int[][] dp = new int[s1.length() + 1][s2.length() + 1];

            for (int i = 0; i <= s1.length(); i++) {
                dp[i][0] = i;
            }
            for (int j = 0; j <= s2.length(); j++) {
                dp[0][j] = j;
            }

            for (int i = 1; i <= s1.length(); i++) {
                for (int j = 1; j <= s2.length(); j++) {
                    int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                    );
                }
            }

            return dp[s1.length()][s2.length()];
        }
    }
}
