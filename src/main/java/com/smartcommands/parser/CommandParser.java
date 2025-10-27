package com.smartcommands.parser;

import com.smartcommands.model.CommandStructure;
import com.smartcommands.service.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses command strings into structured CommandStructure objects.
 * Handles quoted arguments, flags, and multi-word commands.
 */
@Component
public class CommandParser {
    private static final Logger logger = LoggerFactory.getLogger(CommandParser.class);

    // Ollama service for intelligent command correction
    private final OllamaService ollamaService;
    
    // Cache for Ollama responses to improve performance
    private final ConcurrentHashMap<String, String> correctionCache = new ConcurrentHashMap<>();

    // Commands that typically have subcommands
    private static final Set<String> SUBCOMMAND_COMMANDS = Set.of(
        "docker", "git", "kubectl", "npm", "cargo", "systemctl",
        "brew", "apt", "yum", "pip", "go", "mvn", "gradle"
    );

    // Pattern to match quoted strings, flags, and regular tokens
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "'([^']*)'|\"([^\"]*)\"|(-{1,2}\\w+(?:=\\S+)?)|([^\\s]+)"
    );

    @Autowired
    public CommandParser(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    /**
     * Parse a command string into a structured representation
     */
    public CommandStructure parse(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }

        String trimmedCommand = command.trim();
        List<String> tokens = tokenize(trimmedCommand);

        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("No tokens found in command");
        }

        CommandStructure.Builder builder = CommandStructure.builder()
            .rawCommand(trimmedCommand)
            .baseCommand(tokens.get(0));

        // Determine if this command typically has subcommands
        boolean expectsSubcommand = SUBCOMMAND_COMMANDS.contains(tokens.get(0).toLowerCase());

        int currentIndex = 1;

        // Parse subcommand if expected and next token is not a flag
        if (expectsSubcommand && tokens.size() > 1 && !isFlag(tokens.get(1))) {
            builder.subcommand(tokens.get(1));
            currentIndex = 2;
        }

        // Parse remaining tokens as flags and arguments
        while (currentIndex < tokens.size()) {
            String token = tokens.get(currentIndex);

            if (isFlag(token)) {
                builder.addFlag(token);
            } else {
                builder.addArgument(token);
            }

            currentIndex++;
        }

        CommandStructure structure = builder.build();
        logger.debug("Parsed command: {} -> {}", trimmedCommand, structure);
        return structure;
    }

    /**
     * Tokenize command string, respecting quotes and special characters
     */
    private List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(command);

        while (matcher.find()) {
            // Check groups in order: single-quoted, double-quoted, flag, regular token
            if (matcher.group(1) != null) {
                tokens.add(matcher.group(1)); // Single-quoted string
            } else if (matcher.group(2) != null) {
                tokens.add(matcher.group(2)); // Double-quoted string
            } else if (matcher.group(3) != null) {
                tokens.add(matcher.group(3)); // Flag (starts with - or --)
            } else if (matcher.group(4) != null) {
                tokens.add(matcher.group(4)); // Regular token
            }
        }

        logger.debug("Tokenized '{}' into: {}", command, tokens);
        return tokens;
    }

    /**
     * Check if a token is a flag (starts with - or --)
     */
    private boolean isFlag(String token) {
        return token.startsWith("-");
    }

    /**
     * Check if a command expects subcommands
     */
    public boolean expectsSubcommand(String baseCommand) {
        return SUBCOMMAND_COMMANDS.contains(baseCommand.toLowerCase());
    }

    /**
     * Get all commands that support subcommands
     */
    public Set<String> getSubcommandCommands() {
        return Set.copyOf(SUBCOMMAND_COMMANDS);
    }

    /**
     * Parse and correct a command by applying intelligent typo correction using Ollama
     * This method preserves all flags and arguments while correcting typos in base command and subcommand
     */
    public CommandStructure correctSubcommandTypos(String command) {
        CommandStructure structure = parse(command);
        
        // If no subcommand, return as-is
        if (!structure.hasSubcommand()) {
            return structure;
        }
        
        // Check cache first
        String cacheKey = buildSubcommandCacheKey(structure.getBaseCommand(), structure.getSubcommand());
        String cachedCorrection = correctionCache.get(cacheKey);
        
        if (cachedCorrection != null) {
            logger.debug("Using cached subcommand correction: {} -> {}", structure.getSubcommand(), cachedCorrection);
            
            // Only create corrected structure if correction is different
            if (cachedCorrection.equals(structure.getSubcommand())) {
                return structure; // No correction needed
            }
            
            correctionCache.put(cacheKey, cachedCorrection);
            
            return createCorrectedStructure(structure, structure.getBaseCommand(), cachedCorrection);
        }
        
        // Use Ollama for intelligent correction
        String correctedSubcommand = correctSubcommandWithOllama(structure);
        
        if (correctedSubcommand != null && !correctedSubcommand.equals(structure.getSubcommand())) {
            logger.info("Corrected subcommand typo using Ollama: {} {} -> {}", 
                structure.getBaseCommand(), structure.getSubcommand(), 
                structure.getBaseCommand(), correctedSubcommand);
            
            // Cache the correction
            correctionCache.put(cacheKey, correctedSubcommand);
            
            return createCorrectedStructure(structure, structure.getBaseCommand(), correctedSubcommand);
        }
        
        return structure;
    }

    /**
     * Parse and correct a command by applying intelligent typo correction using Ollama
     * This method preserves all other parts while correcting typos in base command
     */
    public CommandStructure correctBaseCommandTypos(String command) {
        CommandStructure structure = parse(command);
        
        // Check cache first
        String cacheKey = buildBaseCommandCacheKey(structure.getBaseCommand());
        String cachedCorrection = correctionCache.get(cacheKey);
        
        if (cachedCorrection != null) {
            logger.debug("Using cached base command correction: {} -> {}", structure.getBaseCommand(), cachedCorrection);
            
            // Only create corrected structure if correction is different
            if (cachedCorrection.equals(structure.getBaseCommand())) {
                return structure; // No correction needed
            }
            
            return createCorrectedStructure(structure, cachedCorrection, structure.getSubcommand());
        }
        
        // Use Ollama for intelligent correction
        String correctedBaseCommand = correctBaseCommandWithOllama(structure);
        
        if (correctedBaseCommand != null && !correctedBaseCommand.equals(structure.getBaseCommand())) {
            logger.info("Corrected base command typo using Ollama: {} -> {}", 
                structure.getBaseCommand(), correctedBaseCommand);
            
            // Cache the correction
            correctionCache.put(cacheKey, correctedBaseCommand);
            
            return createCorrectedStructure(structure, correctedBaseCommand, structure.getSubcommand());
        }
        
        return structure;
    }

    /**
     * Use Ollama to correct subcommand typos intelligently
     */
    private String correctSubcommandWithOllama(CommandStructure structure) {
        if (!ollamaService.isOllamaRunning()) {
            logger.debug("Ollama is not running, using fallback correction");
            return getFallbackSubcommandCorrection(structure.getBaseCommand(), structure.getSubcommand());
        }

        String prompt = buildSubcommandCorrectionPrompt(structure);
        
        try {
            String response = ollamaService.generateCommandSuggestion(structure.getRawCommand(), prompt);
            if (response != null && !response.trim().isEmpty()) {
                // Extract the corrected subcommand from Ollama's response
                return extractSubcommandFromResponse(response, structure.getBaseCommand());
            }
        } catch (Exception e) {
            logger.warn("Failed to get subcommand correction from Ollama: {}", e.getMessage());
        }
        
        // Fallback to basic correction if Ollama fails
        return getFallbackSubcommandCorrection(structure.getBaseCommand(), structure.getSubcommand());
    }

    /**
     * Use Ollama to correct base command typos intelligently
     */
    private String correctBaseCommandWithOllama(CommandStructure structure) {
        if (!ollamaService.isOllamaRunning()) {
            logger.debug("Ollama is not running, using fallback correction");
            return getFallbackBaseCommandCorrection(structure.getBaseCommand());
        }

        String prompt = buildBaseCommandCorrectionPrompt(structure);
        
        try {
            String response = ollamaService.generateCommandSuggestion(structure.getRawCommand(), prompt);
            if (response != null && !response.trim().isEmpty()) {
                // Extract the corrected base command from Ollama's response
                return extractBaseCommandFromResponse(response);
            }
        } catch (Exception e) {
            logger.warn("Failed to get base command correction from Ollama: {}", e.getMessage());
        }
        
        // Fallback to basic correction if Ollama fails
        return getFallbackBaseCommandCorrection(structure.getBaseCommand());
    }

    /**
     * Build a prompt for subcommand correction
     */
    private String buildSubcommandCorrectionPrompt(CommandStructure structure) {
        return String.format(
            "You are a Linux/macOS command line expert. The user entered: '%s'\n" +
            "The base command is: '%s'\n" +
            "The subcommand is: '%s'\n" +
            "Flags: %s\n" +
            "Arguments: %s\n\n" +
            "If the subcommand '%s' is a typo, correct it. If it's correct, return it unchanged.\n" +
            "IMPORTANT: Respond with ONLY the corrected subcommand word. No base command, no flags, no arguments.\n" +
            "Just the single corrected subcommand word. Nothing else.\n" +
            "Example: if subcommand is 'sp', respond with 'ps'\n" +
            "Example: if subcommand is 'stauts', respond with 'status'\n" +
            "Example: if subcommand is 'get', respond with 'get'",
            structure.getRawCommand(),
            structure.getBaseCommand(),
            structure.getSubcommand(),
            structure.getFlags(),
            structure.getArguments(),
            structure.getSubcommand()
        );
    }

    /**
     * Build a prompt for base command correction
     */
    private String buildBaseCommandCorrectionPrompt(CommandStructure structure) {
        return String.format(
            "You are a Linux/macOS command line expert. The user entered: '%s'\n" +
            "The base command is: '%s'\n" +
            "Subcommand: %s\n" +
            "Flags: %s\n" +
            "Arguments: %s\n\n" +
            "If the base command '%s' is a typo, correct it. If it's correct, return it unchanged.\n" +
            "IMPORTANT: Respond with ONLY the corrected base command word. No subcommand, no flags, no arguments.\n" +
            "Just the single corrected base command word. Nothing else.\n" +
            "Example: if base command is 'lss', respond with 'ls'\n" +
            "Example: if base command is 'gti', respond with 'git'\n" +
            "Example: if base command is 'docker', respond with 'docker'",
            structure.getRawCommand(),
            structure.getBaseCommand(),
            structure.getSubcommand(),
            structure.getFlags(),
            structure.getArguments(),
            structure.getBaseCommand()
        );
    }

    /**
     * Extract subcommand from Ollama response
     */
    private String extractSubcommandFromResponse(String response, String baseCommand) {
        String cleaned = response.trim();
        
        // Remove any JSON response format if present
        if (cleaned.startsWith("{\"type\":")) {
            // Extract message from JSON response
            int messageStart = cleaned.indexOf("\"message\":\"Command ");
            if (messageStart != -1) {
                messageStart += "\"message\":\"Command ".length();
                int messageEnd = cleaned.indexOf("\"", messageStart);
                if (messageEnd != -1) {
                    cleaned = cleaned.substring(messageStart, messageEnd);
                }
            }
        }
        
        // Remove base command prefix if present
        if (cleaned.startsWith(baseCommand + " ")) {
            cleaned = cleaned.substring(baseCommand.length() + 1);
        }
        
        // Extract first word as subcommand
        String[] parts = cleaned.split("\\s+");
        if (parts.length > 0) {
            String subcommand = parts[0].trim();
            
            // If subcommand is same as base command, it means no subcommand was found
            if (subcommand.equalsIgnoreCase(baseCommand)) {
                return null; // No subcommand found
            }
            
            return subcommand;
        }
        
        return cleaned;
    }

    /**
     * Extract base command from Ollama response
     */
    private String extractBaseCommandFromResponse(String response) {
        String cleaned = response.trim();
        
        // Remove any JSON response format if present
        if (cleaned.startsWith("{\"type\":")) {
            // Extract message from JSON response
            int messageStart = cleaned.indexOf("\"message\":\"Command ");
            if (messageStart != -1) {
                messageStart += "\"message\":\"Command ".length();
                int messageEnd = cleaned.indexOf("\"", messageStart);
                if (messageEnd != -1) {
                    cleaned = cleaned.substring(messageStart, messageEnd);
                }
            }
        }
        
        // Extract first word as base command
        String[] parts = cleaned.split("\\s+");
        if (parts.length > 0) {
            return parts[0].trim();
        }
        
        return cleaned;
    }

    /**
     * Create a corrected CommandStructure with new base command and/or subcommand
     */
    private CommandStructure createCorrectedStructure(CommandStructure original, String correctedBaseCommand, String correctedSubcommand) {
        return CommandStructure.builder()
            .rawCommand(original.getRawCommand())
            .baseCommand(correctedBaseCommand)
            .subcommand(correctedSubcommand)
            .addFlags(original.getFlags())
            .addArguments(original.getArguments())
            .build();
    }

    /**
     * Build cache key for subcommand corrections
     */
    private String buildSubcommandCacheKey(String baseCommand, String subcommand) {
        return "subcmd:" + baseCommand.toLowerCase() + ":" + subcommand.toLowerCase();
    }

    /**
     * Build cache key for base command corrections
     */
    private String buildBaseCommandCacheKey(String baseCommand) {
        return "base:" + baseCommand.toLowerCase();
    }

    /**
     * Clear the correction cache (useful for testing or when Ollama model changes)
     */
    public void clearCorrectionCache() {
        correctionCache.clear();
        logger.info("Correction cache cleared");
    }

    /**
     * Get cache statistics for monitoring
     */
    public int getCacheSize() {
        return correctionCache.size();
    }

    /**
     * Fallback subcommand correction when Ollama is not available
     * Provides basic typo correction for common cases
     */
    private String getFallbackSubcommandCorrection(String baseCommand, String subcommand) {
        switch (baseCommand.toLowerCase()) {
            case "docker":
                switch (subcommand.toLowerCase()) {
                    case "sp": case "pss": return "ps";
                    case "runn": case "rnu": return "run";
                    case "buld": case "biuld": return "build";
                    case "psuh": case "puh": return "push";
                    case "pll": case "plul": return "pull";
                    case "exce": case "ecex": return "exec";
                    case "iamges": case "imgaes": return "images";
                    case "compsoe": case "compse": return "compose";
                    default: return subcommand;
                }
            case "git":
                switch (subcommand.toLowerCase()) {
                    case "psuh": case "puh": case "puhs": case "phus": case "oush": return "push";
                    case "pll": case "plul": case "lul": case "plll": return "pull";
                    case "comit": case "commti": case "commi": case "cmomit": return "commit";
                    case "stauts": case "staus": case "sttus": case "statsu": return "status";
                    case "checkotu": case "chekout": case "checkou": case "chekcout": return "checkout";
                    case "branhc": case "branc": case "brnch": return "branch";
                    case "mereg": case "merg": case "mrege": return "merge";
                    case "rebsae": case "reabse": case "rebas": return "rebase";
                    case "fetc": case "ftech": case "fech": return "fetch";
                    case "cloen": case "lcone": case "clon": return "clone";
                    default: return subcommand;
                }
            case "kubectl":
                switch (subcommand.toLowerCase()) {
                    case "gt": case "gte": case "ge": return "get";
                    case "desribe": case "descirbe": case "descrbie": return "describe";
                    case "crete": case "craete": case "creat": return "create";
                    case "delte": case "delet": case "deleet": return "delete";
                    case "appl": case "appyl": case "aply": return "apply";
                    case "excec": case "exce": return "exec";
                    default: return subcommand;
                }
            case "npm":
                switch (subcommand.toLowerCase()) {
                    case "isntall": case "instal": case "instll": return "install";
                    case "unsintall": case "unintsall": case "uninstal": return "uninstall";
                    case "udpate": case "updte": case "updaet": return "update";
                    default: return subcommand;
                }
            case "cargo":
                switch (subcommand.toLowerCase()) {
                    case "biuld": case "buld": case "buidl": return "build";
                    case "rnu": case "runn": case "rn": return "run";
                    case "tset": case "tets": case "tesst": return "test";
                    default: return subcommand;
                }
            default:
                return subcommand;
        }
    }

    /**
     * Fallback base command correction when Ollama is not available
     * Provides basic typo correction for common cases
     */
    private String getFallbackBaseCommandCorrection(String baseCommand) {
        switch (baseCommand.toLowerCase()) {
            case "lss": case "lsl": case "sl": case "lls": return "ls";
            case "gti": case "igt": return "git";
            case "mdkir": case "mddir": return "mkdir";
            case "rrm": case "rmr": case "rmrr": return "rm";
            case "catt": case "caat": return "cat";
            case "grepp": case "gerp": return "grep";
            case "finnd": case "findd": return "find";
            case "doker": case "dokcer": case "dcoker": return "docker";
            case "kuebctl": case "kubetcl": case "kubeclt": return "kubectl";
            default:
                return baseCommand; // Return original if no correction needed
        }
    }
}