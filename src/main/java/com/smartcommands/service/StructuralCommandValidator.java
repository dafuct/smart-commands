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
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.text.similarity.LevenshteinDistance;


@Service
public class StructuralCommandValidator {
    private static final Logger logger = LoggerFactory.getLogger(StructuralCommandValidator.class);
    private static final int MAX_EDIT_DISTANCE = 2;
    private static final double TRANSPOSITION_BONUS = 15.0;
    private static final double FIRST_CHAR_BONUS = 3.0;
    private static final double LAST_CHAR_BONUS = 2.0;
    private static final int DISTANCE_CACHE_MAX_SIZE = 5_000;

    private final ConcurrentHashMap<DistanceKey, Integer> distanceCache = new ConcurrentHashMap<>();

    private record DistanceKey(String a, String b) {
        static DistanceKey of(String s1, String s2) { return (s1.compareTo(s2) <= 0) ? new DistanceKey(s1, s2) : new DistanceKey(s2, s1); }
    }

    private record SimilarityMatch(String candidate, int distance, double score) implements Comparable<SimilarityMatch> {
        @Override
        public int compareTo(SimilarityMatch other) {
            return Double.compare(this.score, other.score);
        }
    }

    private final CommandParser commandParser;
    private final CommandMetadataRepository metadataRepository;

    @Autowired
    public StructuralCommandValidator(CommandParser commandParser,
                                     CommandMetadataRepository metadataRepository) {
        this.commandParser = commandParser;
        this.metadataRepository = metadataRepository;
    }

    public Optional<CommandSuggestion> validateStructure(String command) {
        try {
            CommandStructure structure = commandParser.parse(command);

            return metadataRepository.getMetadata(structure.getBaseCommand())
                .flatMap(metadata -> validateWithMetadata(structure, metadata));

        } catch (Exception e) {
            logger.error("Error during structural validation of: {}", command, e);
            return Optional.empty();
        }
    }

    private Optional<CommandSuggestion> validateWithMetadata(
            CommandStructure structure, CommandMetadata metadata) {

        return Optional.of(structure)
            .filter(CommandStructure::hasSubcommand)
            .flatMap(s -> validateSubcommand(s, metadata))
            .or(() -> validateFlags(structure, metadata));
    }

    private Optional<CommandSuggestion> validateSubcommand(
            CommandStructure structure, CommandMetadata metadata) {

        String subcommand = structure.getSubcommand();

        if (metadata.isValidSubcommand(subcommand)) {
            return Optional.empty();
        }

        logger.info("Invalid subcommand detected: {} {}", structure.getBaseCommand(), subcommand);

        return findClosestMatch(subcommand, metadata.getValidSubcommands())
            .map(correctedSubcommand -> createSubcommandSuggestion(structure, correctedSubcommand));
    }

    private CommandSuggestion createSubcommandSuggestion(
            CommandStructure structure, String correctedSubcommand) {

        String correctedCommand = structure.reconstructWithCorrectedSubcommand(correctedSubcommand);

        logger.info("Suggesting correction: {} -> {}", structure.getRawCommand(), correctedCommand);

        return CommandSuggestion.correction(structure.getRawCommand(), correctedCommand);
    }

    private Optional<CommandSuggestion> validateFlags(
            CommandStructure structure, CommandMetadata metadata) {

        if (!structure.hasFlags()) {
            return Optional.empty();
        }

        return structure.getFlags().stream()
            .filter(flag -> !metadata.isValidFlag(flag))
            .findFirst()
            .flatMap(invalidFlag -> correctInvalidFlag(structure, metadata, invalidFlag));
    }

    private Optional<CommandSuggestion> correctInvalidFlag(
            CommandStructure structure, CommandMetadata metadata, String invalidFlag) {

        logger.info("Invalid flag detected: {} for command {}", invalidFlag, structure.getBaseCommand());

        return findClosestMatch(invalidFlag, metadata.getValidFlags())
            .map(correctedFlag -> buildCorrectedFlagCommand(structure, invalidFlag, correctedFlag));
    }

    private CommandSuggestion buildCorrectedFlagCommand(
            CommandStructure structure, String invalidFlag, String correctedFlag) {

        CommandStructure.Builder builder = CommandStructure.builder()
            .rawCommand(structure.getRawCommand())
            .baseCommand(structure.getBaseCommand());

        Optional.ofNullable(structure.getSubcommand())
            .filter(sub -> !sub.isEmpty())
            .ifPresent(builder::subcommand);

        structure.getFlags().stream()
            .map(flag -> flag.equals(invalidFlag) ? correctedFlag : flag)
            .forEach(builder::addFlag);

        structure.getArguments().forEach(builder::addArgument);

        CommandStructure correctedStructure = builder.build();
        String correctedCommand = correctedStructure.reconstruct();

        logger.info("Suggesting flag correction: {} -> {}", structure.getRawCommand(), correctedCommand);

        return CommandSuggestion.correction(structure.getRawCommand(), correctedCommand);
    }

    private Optional<String> findClosestMatch(String input, Set<String> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        String inputLower = input.toLowerCase();

        return candidates.stream()
            .map(candidate -> {
                String candidateLower = candidate.toLowerCase();
                int distance = levenshteinDistance(inputLower, candidateLower);
                double score = calculateSimilarityScore(inputLower, candidateLower, distance);
                return new SimilarityMatch(candidate, distance, score);
            })
            .filter(match -> match.distance() <= MAX_EDIT_DISTANCE)
            .min(SimilarityMatch::compareTo)
            .map(SimilarityMatch::candidate);
    }

    private double calculateSimilarityScore(String input, String candidate, int distance) {
        double score = distance * 10.0;

        if (isTransposition(input, candidate)) {
            score -= TRANSPOSITION_BONUS;
        }

        if (!input.isEmpty() && !candidate.isEmpty() && input.charAt(0) == candidate.charAt(0)) {
            score -= FIRST_CHAR_BONUS;
        }

        if (!input.isEmpty() && !candidate.isEmpty()
                && input.charAt(input.length() - 1) == candidate.charAt(candidate.length() - 1)) {
            score -= LAST_CHAR_BONUS;
        }

        score += Math.abs(input.length() - candidate.length()) * 2.0;

        return score;
    }

    private boolean isTransposition(String s1, String s2) {
        if (s1.length() != s2.length()) {
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

        return differences == 2
            && transpositionIndex < s1.length() - 1
            && s1.charAt(transpositionIndex) == s2.charAt(transpositionIndex + 1)
            && s1.charAt(transpositionIndex + 1) == s2.charAt(transpositionIndex);
    }

    private int levenshteinDistance(String s1, String s2) {
        if (s1.equals(s2)) {
            return 0;
        }
        if (Math.abs(s1.length() - s2.length()) > MAX_EDIT_DISTANCE) {
            return MAX_EDIT_DISTANCE + 1;
        }
        DistanceKey key = DistanceKey.of(s1, s2);
        Integer cached = distanceCache.get(key);
        if (cached != null) return cached;

        int dist = rawLevenshtein(s1, s2);

        if (distanceCache.size() < DISTANCE_CACHE_MAX_SIZE) {
            distanceCache.putIfAbsent(key, dist);
        }
        return dist;
    }

    private int rawLevenshtein(String s1, String s2) {
        LevenshteinDistance levenshteinDistance = new LevenshteinDistance(MAX_EDIT_DISTANCE + 1);
        int distance = levenshteinDistance.apply(s1, s2);
        if (distance < 0) {
            return MAX_EDIT_DISTANCE + 1;
        }
        return distance;
    }

    public Optional<CommandMetadata> getCommandMetadata(String baseCommand) {
        return metadataRepository.getMetadata(baseCommand);
    }

    public boolean hasMetadata(String baseCommand) {
        return metadataRepository.hasMetadata(baseCommand);
    }
}
