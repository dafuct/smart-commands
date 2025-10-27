package com.smartcommands.service;

import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.parser.CommandParser;
import com.smartcommands.repository.CommandMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StructuralCommandValidator
 */
class StructuralCommandValidatorTest {

    private StructuralCommandValidator validator;
    private CommandParser parser;
    private CommandMetadataRepository metadataRepository;
    
    @Mock
    private OllamaService mockOllamaService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockOllamaService.isOllamaRunning()).thenReturn(false);
        parser = new CommandParser(mockOllamaService);
        metadataRepository = new CommandMetadataRepository();
        validator = new StructuralCommandValidator(parser, metadataRepository);
    }

    @Test
    void testValidateDockerPsCommand_Valid() {
        Optional<CommandSuggestion> result = validator.validateStructure("docker ps -a");

        assertTrue(result.isEmpty(), "Valid command should return empty");
    }

    @Test
    void testValidateDockerSpCommand_InvalidSubcommand() {
        Optional<CommandSuggestion> result = validator.validateStructure("docker sp -a");

        // Structural validation should detect the typo and suggest correction
        // "sp" is close enough to "ps" (transposition, distance = 2)
        assertTrue(result.isPresent(), "Structural validation should suggest correction for 'sp' -> 'ps'");
        CommandSuggestion suggestion = result.get();
        assertTrue(suggestion.needsCorrection());
        assertTrue(suggestion.getSuggestion().contains("ps"), "Should suggest 'ps' command");
    }

    @Test
    void testValidateDockerPsWithInvalidFlag() {
        Optional<CommandSuggestion> result = validator.validateStructure("docker ps -x");

        assertTrue(result.isPresent(), "Invalid flag should return suggestion");
        CommandSuggestion suggestion = result.get();
        assertTrue(suggestion.needsCorrection());
        assertNotNull(suggestion.getSuggestion());
    }

    @Test
    void testValidateGitStatusCommand_Valid() {
        Optional<CommandSuggestion> result = validator.validateStructure("git status");

        assertTrue(result.isEmpty(), "Valid command should return empty");
    }

    @Test
    void testValidateGitStatutsCommand_Typo() {
        Optional<CommandSuggestion> result = validator.validateStructure("git statuts");

        // Structural validation should detect the typo and suggest correction
        // "statuts" is close to "status" (1 char difference, distance = 1)
        assertTrue(result.isPresent(), "Structural validation should suggest correction for 'statuts' -> 'status'");
        CommandSuggestion suggestion = result.get();
        assertTrue(suggestion.needsCorrection());
        assertTrue(suggestion.getSuggestion().contains("status"), "Should suggest 'status' command");
    }

    @Test
    void testValidateKubectlGetPods_Valid() {
        Optional<CommandSuggestion> result = validator.validateStructure("kubectl get pods");

        assertTrue(result.isEmpty(), "Valid command should return empty");
    }

    @Test
    void testValidateKubectlGte_InvalidSubcommand() {
        Optional<CommandSuggestion> result = validator.validateStructure("kubectl gte pods");

        // Structural validation should detect the typo and suggest correction
        // "gte" is close to "get" (1 char extra, distance = 1)
        assertTrue(result.isPresent(), "Structural validation should suggest correction for 'gte' -> 'get'");
        CommandSuggestion suggestion = result.get();
        assertTrue(suggestion.needsCorrection());
        assertTrue(suggestion.getSuggestion().contains("get"), "Should suggest 'get' command");
    }

    @Test
    void testValidateUnknownCommand_NoMetadata() {
        Optional<CommandSuggestion> result = validator.validateStructure("unknowncommand test");

        assertTrue(result.isEmpty(), "Unknown command without metadata should return empty");
    }

    @Test
    void testValidateNpmInstall_Valid() {
        Optional<CommandSuggestion> result = validator.validateStructure("npm install express");

        assertTrue(result.isEmpty(), "Valid npm command should return empty");
    }

    @Test
    void testValidateNpmInstlal_Typo() {
        Optional<CommandSuggestion> result = validator.validateStructure("npm instlal express");

        // Structural validation should detect the typo and suggest correction
        // "instlal" is close to "install" (1 char transposition, distance = 2)
        assertTrue(result.isPresent(), "Structural validation should suggest correction for 'instlal' -> 'install'");
        CommandSuggestion suggestion = result.get();
        assertTrue(suggestion.needsCorrection());
        assertTrue(suggestion.getSuggestion().contains("install"), "Should suggest 'install' command");
    }

    @Test
    void testValidateDockerWithMultipleFlags_AllValid() {
        Optional<CommandSuggestion> result =
            validator.validateStructure("docker ps -a -q -s");

        assertTrue(result.isEmpty(), "All valid flags should return empty");
    }

    @Test
    void testValidateDockerWithMixedFlags_OneInvalid() {
        Optional<CommandSuggestion> result =
            validator.validateStructure("docker ps -a -x -q");

        // Structural validation should detect invalid flags
        assertTrue(result.isPresent(), "Invalid flag should be detected");
        CommandSuggestion suggestion = result.get();
        assertTrue(suggestion.needsCorrection());
    }

    @Test
    void testLevenshteinDistance_ExactMatch() {
        // Testing through validateStructure which uses Levenshtein internally
        Optional<CommandSuggestion> result = validator.validateStructure("docker ps");
        assertTrue(result.isEmpty(), "Exact match should be valid");
    }

    @Test
    void testLevenshteinDistance_OneCharDifference() {
        // 'sp' vs 'ps' = 2 transpositions = distance 2
        Optional<CommandSuggestion> result = validator.validateStructure("docker sp");
        assertTrue(result.isPresent(), "One char difference should suggest correction");
    }

    @Test
    void testLevenshteinDistance_TooManyDifferences() {
        // If difference is too large (> 2), might not suggest correction
        Optional<CommandSuggestion> result = validator.validateStructure("docker xyz");
        // This might or might not return a suggestion depending on closest match
        // Just verify it doesn't crash
        assertNotNull(result);
    }

    @Test
    void testHasMetadata() {
        assertTrue(validator.hasMetadata("docker"));
        assertTrue(validator.hasMetadata("git"));
        assertTrue(validator.hasMetadata("kubectl"));
        assertFalse(validator.hasMetadata("unknowncommand"));
    }

    @Test
    void testGetCommandMetadata() {
        assertTrue(validator.getCommandMetadata("docker").isPresent());
        assertTrue(validator.getCommandMetadata("git").isPresent());
        assertFalse(validator.getCommandMetadata("unknowncommand").isPresent());
    }

    @Test
    void testValidateLongFlags() {
        Optional<CommandSuggestion> result =
            validator.validateStructure("docker ps --all --quiet");

        assertTrue(result.isEmpty(), "Valid long flags should return empty");
    }

    @Test
    void testValidateFlagEqualsFormat() {
        Optional<CommandSuggestion> result =
            validator.validateStructure("docker run --name=myapp nginx");

        assertTrue(result.isEmpty(), "Flag with equals should be valid");
    }
}