package com.smartcommands.service;

import com.smartcommands.model.CommandMetadata;
import com.smartcommands.model.CommandStructure;
import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.parser.CommandParser;
import com.smartcommands.repository.CommandMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Structural validator that checks commands against metadata repository
 * This is Tier 2 validation - reference-based validation
 */
@Service
public class StructuralCommandValidator {
    private static final Logger logger = LoggerFactory.getLogger(StructuralCommandValidator.class);

    private final CommandParser commandParser;
    private final CommandMetadataRepository metadataRepository;

    @Autowired
    public StructuralCommandValidator(CommandParser commandParser,
                                     CommandMetadataRepository metadataRepository) {
        this.commandParser = commandParser;
        this.metadataRepository = metadataRepository;
    }

    /**
     * Validate command structure and return suggestion if corrections needed
     * Returns Optional.empty() if command is valid or metadata not available
     */
    public Optional<CommandSuggestion> validateStructure(String command) {
        try {
            CommandStructure structure = commandParser.parse(command);
            logger.debug("Validating structure: {}", structure);

            // Check if we have metadata for this command
            Optional<CommandMetadata> metadataOpt = metadataRepository
                .getMetadata(structure.getBaseCommand());

            if (metadataOpt.isEmpty()) {
                logger.debug("No metadata available for command: {}", structure.getBaseCommand());
                return Optional.empty();
            }

            CommandMetadata metadata = metadataOpt.get();

            // Validate subcommand if present
            if (structure.hasSubcommand()) {
                Optional<CommandSuggestion> subcommandValidation =
                    validateSubcommand(structure, metadata);
                if (subcommandValidation.isPresent()) {
                    return subcommandValidation;
                }
            }

            // Validate flags
            Optional<CommandSuggestion> flagValidation =
                validateFlags(structure, metadata);
            if (flagValidation.isPresent()) {
                return flagValidation;
            }

            // Command is structurally valid
            logger.debug("Command passed structural validation: {}", command);
            return Optional.empty();

        } catch (Exception e) {
            logger.error("Error during structural validation of: {}", command, e);
            return Optional.empty();
        }
    }

    /**
     * Validate subcommand against metadata
     */
    private Optional<CommandSuggestion> validateSubcommand(
            CommandStructure structure, CommandMetadata metadata) {

        String subcommand = structure.getSubcommand();
        String baseCommand = structure.getBaseCommand();

        if (!metadata.isValidSubcommand(subcommand)) {
            logger.info("Invalid subcommand detected: {} {}", baseCommand, subcommand);

            // Find closest match using edit distance
            Optional<String> closestMatch = findClosestMatch(
                subcommand, metadata.getValidSubcommands());

            if (closestMatch.isPresent()) {
                String correctedSubcommand = closestMatch.get();
                String correctedCommand = structure.reconstructWithCorrectedSubcommand(correctedSubcommand);

                logger.info("Suggesting correction: {} -> {}",
                    structure.getRawCommand(), correctedCommand);

                return Optional.of(CommandSuggestion.correction(
                    structure.getRawCommand(),
                    correctedCommand
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Validate flags against metadata
     */
    private Optional<CommandSuggestion> validateFlags(
            CommandStructure structure, CommandMetadata metadata) {

        if (!structure.hasFlags()) {
            return Optional.empty();
        }

        for (String flag : structure.getFlags()) {
            if (!metadata.isValidFlag(flag)) {
                logger.info("Invalid flag detected: {} for command {}",
                    flag, structure.getBaseCommand());

                // Find closest match
                Optional<String> closestMatch = findClosestMatch(
                    flag, metadata.getValidFlags());

                if (closestMatch.isPresent()) {
                    String correctedFlag = closestMatch.get();
                    // Create a new structure with the corrected flag
                    CommandStructure.Builder builder = CommandStructure.builder()
                        .rawCommand(structure.getRawCommand())
                        .baseCommand(structure.getBaseCommand());
                    
                    if (structure.hasSubcommand()) {
                        builder.subcommand(structure.getSubcommand());
                    }
                    
                    // Add corrected flag instead of the invalid one
                    for (String f : structure.getFlags()) {
                        if (f.equals(flag)) {
                            builder.addFlag(correctedFlag);
                        } else {
                            builder.addFlag(f);
                        }
                    }
                    
                    // Add all original arguments
                    for (String arg : structure.getArguments()) {
                        builder.addArgument(arg);
                    }
                    
                    CommandStructure correctedStructure = builder.build();
                    String correctedCommand = correctedStructure.reconstruct();

                    logger.info("Suggesting flag correction: {} -> {}",
                        structure.getRawCommand(), correctedCommand);

                    return Optional.of(CommandSuggestion.correction(
                        structure.getRawCommand(),
                        correctedCommand
                    ));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Find closest matching string using enhanced similarity scoring
     * Considers:
     * - Levenshtein distance
     * - Common character positions
     * - Transposition detection (e.g., "ps" vs "sp")
     * Only returns matches within threshold
     */
    private Optional<String> findClosestMatch(String input, Set<String> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int minDistance = Integer.MAX_VALUE;
        String closestMatch = null;
        double bestScore = Double.MAX_VALUE;

        for (String candidate : candidates) {
            int distance = levenshteinDistance(
                input.toLowerCase(),
                candidate.toLowerCase()
            );

            // Calculate similarity score combining distance and other factors
            double score = calculateSimilarityScore(
                input.toLowerCase(),
                candidate.toLowerCase(),
                distance
            );

            if (score < bestScore) {
                bestScore = score;
                minDistance = distance;
                closestMatch = candidate;
            }
        }

        // Only suggest if within reasonable threshold (edit distance <= 2)
        if (minDistance <= 2 && closestMatch != null) {
            logger.debug("Found close match: {} -> {} (distance: {}, score: {})",
                input, closestMatch, minDistance, bestScore);
            return Optional.of(closestMatch);
        }

        logger.debug("No close match found for: {} (min distance: {})",
            input, minDistance);
        return Optional.empty();
    }

    /**
     * Calculate similarity score considering multiple factors
     * Lower score = better match
     */
    private double calculateSimilarityScore(String input, String candidate, int distance) {
        double score = distance * 10.0; // Base score from edit distance

        // Bonus for transposition (common typo pattern like "sp" vs "ps")
        if (isTransposition(input, candidate)) {
            score -= 15.0; // Strong preference for transpositions
        }

        // Bonus for matching first character (common in command completion)
        if (input.length() > 0 && candidate.length() > 0 
            && input.charAt(0) == candidate.charAt(0)) {
            score -= 3.0;
        }

        // Bonus for matching last character
        if (input.length() > 0 && candidate.length() > 0
            && input.charAt(input.length() - 1) == candidate.charAt(candidate.length() - 1)) {
            score -= 2.0;
        }

        // Bonus for similar length
        int lengthDiff = Math.abs(input.length() - candidate.length());
        score += lengthDiff * 2.0;

        return score;
    }

    /**
     * Check if the difference between two strings is just a transposition
     * e.g., "sp" and "ps", "tset" and "test"
     */
    private boolean isTransposition(String s1, String s2) {
        if (Math.abs(s1.length() - s2.length()) != 0) {
            return false;
        }

        if (s1.length() < 2) {
            return false;
        }

        int differences = 0;
        int transpositionIndex = -1;

        for (int i = 0; i < s1.length(); i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                differences++;
                if (transpositionIndex == -1) {
                    transpositionIndex = i;
                }
            }
        }

        // If exactly 2 adjacent characters differ, check if they're swapped
        if (differences == 2 && transpositionIndex >= 0 && transpositionIndex < s1.length() - 1) {
            return s1.charAt(transpositionIndex) == s2.charAt(transpositionIndex + 1)
                && s1.charAt(transpositionIndex + 1) == s2.charAt(transpositionIndex);
        }

        return false;
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
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Get metadata for command if available
     */
    public Optional<CommandMetadata> getCommandMetadata(String baseCommand) {
        return metadataRepository.getMetadata(baseCommand);
    }

    /**
     * Check if metadata exists for command
     */
    public boolean hasMetadata(String baseCommand) {
        return metadataRepository.hasMetadata(baseCommand);
    }
}