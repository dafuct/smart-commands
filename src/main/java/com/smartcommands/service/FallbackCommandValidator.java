package com.smartcommands.service;

import com.smartcommands.model.CommandSuggestion;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.text.similarity.LevenshteinDistance;

@Component
public class FallbackCommandValidator {
    private static final Map<String, CommandKnowledge> COMMAND_DATABASE = new HashMap<>();

    static {
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

    public CommandSuggestion validate(String command) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
            return CommandSuggestion.error("Command cannot be empty");
        }

        String[] parts = trimmed.split("\\s+");
        String baseCommand = parts[0];

        String correctedBase = checkBaseCommandTypo(baseCommand);
        if (correctedBase != null) {
            String correctedCommand = trimmed.replaceFirst(Pattern.quote(baseCommand), correctedBase);
            return CommandSuggestion.correction(command, correctedCommand);
        }

        CommandKnowledge knowledge = COMMAND_DATABASE.get(baseCommand);
        if (knowledge != null) {
            return validateWithKnowledge(command, parts, knowledge);
        }

        if (isCommonCommand(baseCommand)) {
            return CommandSuggestion.regularCommand(command);
        }

        return CommandSuggestion.error(
            "Unknown command: '" + baseCommand + "'. Type 'sc \"describe what you want to do\"' for help."
        );
    }

    private CommandSuggestion validateWithKnowledge(String originalCommand, String[] parts, CommandKnowledge knowledge) {
        if (parts.length < 2) {
            return CommandSuggestion.regularCommand(originalCommand);
        }

        String baseCommand = parts[0];
        String subcommand = parts[1];

        if (subcommand.startsWith("-")) {
            return CommandSuggestion.regularCommand(originalCommand);
        }

        String correctedSubcommand = knowledge.correctSubcommandTypo(subcommand);
        if (correctedSubcommand != null) {
            String correctedCommand = originalCommand.replaceFirst(
                Pattern.quote(baseCommand + " " + subcommand),
                baseCommand + " " + correctedSubcommand
            );
            return CommandSuggestion.correction(originalCommand, correctedCommand);
        }

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

        if (parts.length > 2) {
            String invalidFlag = validateFlags(parts, 2, knowledge, subcommand);
            if (invalidFlag != null) {
                String suggestedFlag = knowledge.findSimilarFlag(subcommand, invalidFlag);
                if (suggestedFlag != null) {
                    String correctedCommand = originalCommand.replace(invalidFlag, suggestedFlag);
                    return CommandSuggestion.correction(originalCommand, correctedCommand);
                }
            }
        }

        return CommandSuggestion.regularCommand(originalCommand);
    }

    private String validateFlags(String[] parts, int startIndex, CommandKnowledge knowledge, String subcommand) {
        for (int i = startIndex; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("-")) {
                if (!knowledge.isValidFlag(subcommand, part) && !isCommonFlag(part)) {
                    return part;
                }
            }
        }
        return null;
    }

    private boolean isCommonFlag(String flag) {
        return flag.equals("-h") || flag.equals("--help") ||
               flag.equals("-v") || flag.equals("--version") ||
               flag.equals("-V") || flag.equals("--verbose");
    }

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

        return commonTypos.get(command);
    }

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

    private record CommandKnowledge(Set<String> validSubcommands, Map<String, Set<String>> validFlags,
                                    Map<String, String> subcommandTypos) {
            private static final int MAX_EDIT_DISTANCE = 2;
            private static final LevenshteinDistance LD = new LevenshteinDistance(MAX_EDIT_DISTANCE + 1);

        public boolean isValidSubcommand(String subcommand) {
                return validSubcommands.contains(subcommand);
            }

            public String correctSubcommandTypo(String typo) {
                return subcommandTypos.get(typo);
            }

            public String findSimilarSubcommand(String input) {
                return getMinDistance(input, validSubcommands);
            }

            public boolean isValidFlag(String subcommand, String flag) {
                Set<String> flags = validFlags.get(subcommand);
                if (flags == null) {
                    return true;
                }

                String cleanFlag = flag.split("=")[0];
                return flags.contains(cleanFlag);
            }

            public String findSimilarFlag(String subcommand, String input) {
                Set<String> flags = validFlags.get(subcommand);
                if (flags == null) {
                    return null;
                }

                return getMinDistance(input, flags);
            }

            private String getMinDistance(String input, Set<String> flags) {
                int minDistance = Integer.MAX_VALUE;
                String bestMatch = null;

                for (String validFlag : flags) {
                    int distance = thresholdedDistance(input, validFlag);
                    if (distance < minDistance && distance <= 2) {
                        minDistance = distance;
                        bestMatch = validFlag;
                    }
                }

                return bestMatch;
            }

            private int thresholdedDistance(String a, String b) {
                if (a.equals(b)) {
                    return 0;
                }
                if (Math.abs(a.length() - b.length()) > MAX_EDIT_DISTANCE) return MAX_EDIT_DISTANCE + 1;
                int v = LD.apply(a, b);
                return v < 0 ? (MAX_EDIT_DISTANCE + 1) : v;
            }
    }
}
