package com.smartcommands.parser;

import com.smartcommands.model.CommandStructure;
import com.smartcommands.service.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class CommandParser {
    private static final Logger logger = LoggerFactory.getLogger(CommandParser.class);

    private final OllamaService ollamaService;
    private final ConcurrentHashMap<String, String> correctionCache = new ConcurrentHashMap<>();

    private static final Set<String> SUBCOMMAND_COMMANDS = Set.of(
        "docker", "git", "kubectl", "npm", "cargo", "systemctl",
        "brew", "apt", "yum", "pip", "go", "mvn", "gradle"
    );

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "'([^']*)'|\"([^\"]*)\"|(-{1,2}\\w+(?:=\\S+)?)|([^\\s]+)"
    );

    @Autowired
    public CommandParser(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    public CommandStructure parse(String command) {
        validateCommand(command);

        String trimmedCommand = command.trim();
        List<String> tokens = tokenize(trimmedCommand);

        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("No tokens found in command");
        }

        String baseCommand = tokens.getFirst();
        boolean expectsSubcommand = SUBCOMMAND_COMMANDS.contains(baseCommand.toLowerCase());

        return buildCommandStructure(trimmedCommand, tokens, baseCommand, expectsSubcommand);
    }

    private void validateCommand(String command) {
        Optional.ofNullable(command)
            .map(String::trim)
            .filter(cmd -> !cmd.isEmpty())
            .orElseThrow(() -> new IllegalArgumentException("Command cannot be null or empty"));
    }

    private CommandStructure buildCommandStructure(String rawCommand, List<String> tokens,
                                                   String baseCommand, boolean expectsSubcommand) {
        CommandStructure.Builder builder = CommandStructure.builder()
            .rawCommand(rawCommand)
            .baseCommand(baseCommand);

        int startIndex = processSubcommand(tokens, builder, expectsSubcommand);
        processTokens(tokens, builder, startIndex);

        return builder.build();
    }

    /**
     * Processes subcommand if present and returns the next token index
     */
    private int processSubcommand(List<String> tokens, CommandStructure.Builder builder,
                                  boolean expectsSubcommand) {
        if (expectsSubcommand && tokens.size() > 1) {
            String potentialSubcommand = tokens.get(1);
            if (!isFlag(potentialSubcommand)) {
                builder.subcommand(potentialSubcommand);
                return 2;
            }
        }
        return 1;
    }

    private void processTokens(List<String> tokens, CommandStructure.Builder builder, int startIndex) {
        tokens.subList(startIndex, tokens.size())
            .forEach(token -> {
                if (isFlag(token)) {
                    builder.addFlag(token);
                } else {
                    builder.addArgument(token);
                }
            });
    }

    /**
     * Tokenize command string, respecting quotes and special characters
     */
    private List<String> tokenize(String command) {
        return TOKEN_PATTERN.matcher(command)
            .results()
            .map(matchResult ->
                extractToken(matchResult)
                    .orElseThrow(() -> new IllegalStateException("Failed to extract token from match"))
            )
            .toList();
    }

    private Optional<String> extractToken(java.util.regex.MatchResult matchResult) {
        return Optional.ofNullable(matchResult.group(1))
            .or(() -> Optional.ofNullable(matchResult.group(2)))
            .or(() -> Optional.ofNullable(matchResult.group(3)))
            .or(() -> Optional.ofNullable(matchResult.group(4)));
    }

    private boolean isFlag(String token) {
        return token.startsWith("-");
    }

    public boolean expectsSubcommand(String baseCommand) {
        return SUBCOMMAND_COMMANDS.contains(baseCommand.toLowerCase());
    }

    public Set<String> getSubcommandCommands() {
        return Set.copyOf(SUBCOMMAND_COMMANDS);
    }

    public CommandStructure correctSubcommandTypos(String command) {
        CommandStructure structure = parse(command);

        if (!structure.hasSubcommand()) {
            return structure;
        }

        String cacheKey = buildSubcommandCacheKey(structure.getBaseCommand(), structure.getSubcommand());
        String cachedCorrection = correctionCache.get(cacheKey);
        
        if (cachedCorrection != null) {
            if (cachedCorrection.equals(structure.getSubcommand())) {
                return structure;
            }
            correctionCache.put(cacheKey, cachedCorrection);
            return createCorrectedStructure(structure, structure.getBaseCommand(), cachedCorrection);
        }

        String correctedSubcommand = correctSubcommandWithOllama(structure);
        
        if (correctedSubcommand != null && !correctedSubcommand.equals(structure.getSubcommand())) {
            correctionCache.put(cacheKey, correctedSubcommand);
            
            return createCorrectedStructure(structure, structure.getBaseCommand(), correctedSubcommand);
        }
        
        return structure;
    }

    public CommandStructure correctBaseCommandTypos(String command) {
        CommandStructure structure = parse(command);
        String cacheKey = buildBaseCommandCacheKey(structure.getBaseCommand());
        String cachedCorrection = correctionCache.get(cacheKey);
        
        if (cachedCorrection != null) {
            if (cachedCorrection.equals(structure.getBaseCommand())) {
                return structure;
            }
            
            return createCorrectedStructure(structure, cachedCorrection, structure.getSubcommand());
        }

        String correctedBaseCommand = correctBaseCommandWithOllama(structure);
        
        if (correctedBaseCommand != null && !correctedBaseCommand.equals(structure.getBaseCommand())) {
            correctionCache.put(cacheKey, correctedBaseCommand);
            
            return createCorrectedStructure(structure, correctedBaseCommand, structure.getSubcommand());
        }
        
        return structure;
    }

    private String correctSubcommandWithOllama(CommandStructure structure) {
        if (!ollamaService.isOllamaRunning()) {
            return getFallbackSubcommandCorrection(structure.getBaseCommand(), structure.getSubcommand());
        }

        String prompt = buildSubcommandCorrectionPrompt(structure);
        
        try {
            String response = ollamaService.generateCommandSuggestion(structure.getRawCommand(), prompt);
            if (response != null && !response.trim().isEmpty()) {
                return extractSubcommandFromResponse(response, structure.getBaseCommand());
            }
        } catch (Exception e) {
            logger.warn("Failed to get subcommand correction from Ollama: {}", e.getMessage());
        }

        return getFallbackSubcommandCorrection(structure.getBaseCommand(), structure.getSubcommand());
    }

    private String correctBaseCommandWithOllama(CommandStructure structure) {
        if (!ollamaService.isOllamaRunning()) {
            return getFallbackBaseCommandCorrection(structure.getBaseCommand());
        }

        String prompt = buildBaseCommandCorrectionPrompt(structure);
        
        try {
            String response = ollamaService.generateCommandSuggestion(structure.getRawCommand(), prompt);
            if (response != null && !response.trim().isEmpty()) {
                return extractBaseCommandFromResponse(response);
            }
        } catch (Exception e) {
            logger.warn("Failed to get base command correction from Ollama: {}", e.getMessage());
        }

        return getFallbackBaseCommandCorrection(structure.getBaseCommand());
    }

    private String buildSubcommandCorrectionPrompt(CommandStructure structure) {
        return """
            You are a Linux/macOS command line expert. The user entered: '%s'
            The base command is: '%s'
            The subcommand is: '%s'
            Flags: %s
            Arguments: %s
            
            If the subcommand '%s' is a typo, correct it. If it's correct, return it unchanged.
            IMPORTANT: Respond with ONLY the corrected subcommand word. No base command, no flags, no arguments.
            Just the single corrected subcommand word. Nothing else.
            Example: if subcommand is 'sp', respond with 'ps'
            Example: if subcommand is 'stauts', respond with 'status'
            Example: if subcommand is 'get', respond with 'get'
            """.formatted(
                structure.getRawCommand(),
                structure.getBaseCommand(),
                structure.getSubcommand(),
                structure.getFlags(),
                structure.getArguments(),
                structure.getSubcommand()
            );
    }

    private String buildBaseCommandCorrectionPrompt(CommandStructure structure) {
        return """
            You are a Linux/macOS command line expert. The user entered: '%s'
            The base command is: '%s'
            Subcommand: %s
            Flags: %s
            Arguments: %s
            
            If the base command '%s' is a typo, correct it. If it's correct, return it unchanged.
            IMPORTANT: Respond with ONLY the corrected base command word. No subcommand, no flags, no arguments.
            Just the single corrected base command word. Nothing else.
            Example: if base command is 'lss', respond with 'ls'
            Example: if base command is 'gti', respond with 'git'
            Example: if base command is 'docker', respond with 'docker'
            """.formatted(
                structure.getRawCommand(),
                structure.getBaseCommand(),
                structure.getSubcommand(),
                structure.getFlags(),
                structure.getArguments(),
                structure.getBaseCommand()
            );
    }

    private String extractSubcommandFromResponse(String response, String baseCommand) {
        String cleaned = getCleanedText(response);

        if (cleaned.startsWith(baseCommand + " ")) {
            cleaned = cleaned.substring(baseCommand.length() + 1);
        }

        String[] parts = cleaned.split("\\s+");
        if (parts.length > 0) {
            String subcommand = parts[0].trim();

            if (subcommand.equalsIgnoreCase(baseCommand)) {
                return null;
            }
            
            return subcommand;
        }
        
        return cleaned;
    }

    private String extractBaseCommandFromResponse(String response) {
        String cleaned = getCleanedText(response);

        String[] parts = cleaned.split("\\s+");
        if (parts.length > 0) {
            return parts[0].trim();
        }
        
        return cleaned;
    }

    private String getCleanedText(String response) {
        String cleaned = response.trim();

        if (cleaned.startsWith("{\"type\":")) {
            int messageStart = cleaned.indexOf("\"message\":\"Command ");
            if (messageStart != -1) {
                messageStart += "\"message\":\"Command ".length();
                int messageEnd = cleaned.indexOf("\"", messageStart);
                if (messageEnd != -1) {
                   cleaned = cleaned.substring(messageStart, messageEnd);
                }
            }
        }
        return cleaned;
    }

    private CommandStructure createCorrectedStructure(CommandStructure original, String correctedBaseCommand, String correctedSubcommand) {
        return CommandStructure.builder()
            .rawCommand(original.getRawCommand())
            .baseCommand(correctedBaseCommand)
            .subcommand(correctedSubcommand)
            .addFlags(original.getFlags())
            .addArguments(original.getArguments())
            .build();
    }

    private String buildSubcommandCacheKey(String baseCommand, String subcommand) {
        return "subcmd:" + baseCommand.toLowerCase() + ":" + subcommand.toLowerCase();
    }

    private String buildBaseCommandCacheKey(String baseCommand) {
        return "base:" + baseCommand.toLowerCase();
    }

    public void clearCorrectionCache() {
        correctionCache.clear();
        logger.info("Correction cache cleared");
    }

    public int getCacheSize() {
        return correctionCache.size();
    }

    private String getFallbackSubcommandCorrection(String baseCommand, String subcommand) {
        return switch (baseCommand.toLowerCase()) {
            case "docker" -> switch (subcommand.toLowerCase()) {
                case "sp", "pss" -> "ps";
                case "runn", "rnu" -> "run";
                case "buld", "biuld" -> "build";
                case "psuh", "puh" -> "push";
                case "pll", "plul" -> "pull";
                case "exce", "ecex" -> "exec";
                case "iamges", "imgaes" -> "images";
                case "compsoe", "compse" -> "compose";
                default -> subcommand;
            };
            case "git" -> switch (subcommand.toLowerCase()) {
                case "psuh", "puh", "puhs", "phus", "oush" -> "push";
                case "pll", "plul", "lul", "plll" -> "pull";
                case "comit", "commti", "commi", "cmomit" -> "commit";
                case "stauts", "staus", "sttus", "statsu" -> "status";
                case "checkotu", "chekout", "checkou", "chekcout" -> "checkout";
                case "branhc", "branc", "brnch" -> "branch";
                case "mereg", "merg", "mrege" -> "merge";
                case "rebsae", "reabse", "rebas" -> "rebase";
                case "fetc", "ftech", "fech" -> "fetch";
                case "cloen", "lcone", "clon" -> "clone";
                default -> subcommand;
            };
            case "kubectl" -> switch (subcommand.toLowerCase()) {
                case "gt", "gte", "ge" -> "get";
                case "desribe", "descirbe", "descrbie" -> "describe";
                case "crete", "craete", "creat" -> "create";
                case "delte", "delet", "deleet" -> "delete";
                case "appl", "appyl", "aply" -> "apply";
                case "excec", "exce" -> "exec";
                default -> subcommand;
            };
            case "npm" -> switch (subcommand.toLowerCase()) {
                case "isntall", "instal", "instll" -> "install";
                case "unsintall", "unintsall", "uninstal" -> "uninstall";
                case "udpate", "updte", "updaet" -> "update";
                default -> subcommand;
            };
            case "cargo" -> switch (subcommand.toLowerCase()) {
                case "biuld", "buld", "buidl" -> "build";
                case "rnu", "runn", "rn" -> "run";
                case "tset", "tets", "tesst" -> "test";
                default -> subcommand;
            };
            default -> subcommand;
        };
    }

    private String getFallbackBaseCommandCorrection(String baseCommand) {
        return switch (baseCommand.toLowerCase()) {
            case "lss", "lsl", "sl", "lls" -> "ls";
            case "gti", "igt" -> "git";
            case "mdkir", "mddir" -> "mkdir";
            case "rrm", "rmr", "rmrr" -> "rm";
            case "catt", "caat" -> "cat";
            case "grepp", "gerp" -> "grep";
            case "finnd", "findd" -> "find";
            case "doker", "dokcer", "dcoker" -> "docker";
            case "kuebctl", "kubetcl", "kubeclt" -> "kubectl";
            default -> baseCommand; // Return original if no correction needed
        };
    }
}