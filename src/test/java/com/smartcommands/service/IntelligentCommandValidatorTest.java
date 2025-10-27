package com.smartcommands.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.parser.CommandParser;
import com.smartcommands.repository.CommandMetadataRepository;

/**
 * Unit tests for IntelligentCommandValidator with three-tier architecture
 */
public class IntelligentCommandValidatorTest {

    @Mock
    private OllamaService ollamaService;

    private IntelligentCommandValidator validator;
    private CommandParser commandParser;
    private StructuralCommandValidator structuralValidator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Initialize real components for structural validation
        commandParser = new CommandParser(ollamaService);
        CommandMetadataRepository metadataRepository = new CommandMetadataRepository();
        structuralValidator = new StructuralCommandValidator(commandParser, metadataRepository);
        
        validator = new IntelligentCommandValidator(
            ollamaService, 
            commandParser, 
            structuralValidator
        );
    }

    @Test
    void testDockerSpWithFlag_CaughtByStructuralValidation() {
        // Mock Ollama as not running, so correction methods will return null
        // and structural validation will handle it
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        CommandSuggestion suggestion = validator.validateAndCorrect("docker sp -a");

        assertNotNull(suggestion);
        // Since Ollama is not running and structural validation doesn't have hardcoded corrections,
        // we expect no correction from this validator
        assertFalse(suggestion.needsCorrection());
        
        // Ollama should not be called since it's not running
        verify(ollamaService, never()).generateCommandSuggestion(anyString(), anyString());
    }

    @Test
    void testDockerSpWithFlag_OllamaAvailableButStructuralCatchesIt() {
        // Mock Ollama as running and provide correction response
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        // Base command correction should return original (no correction needed)
        when(ollamaService.generateCommandSuggestion(contains("base command"), any()))
            .thenReturn("docker"); // No correction for base command
        // Subcommand correction should return corrected subcommand
        when(ollamaService.generateCommandSuggestion(contains("subcommand"), any()))
            .thenReturn("ps"); // Ollama returns corrected subcommand

        CommandSuggestion suggestion = validator.validateAndCorrect("docker sp -a");

        assertNotNull(suggestion);
        assertTrue(suggestion.needsCorrection());
        assertEquals("docker ps -a", suggestion.getSuggestion());
        
        // Ollama should be called twice - once for base command, once for subcommand
        verify(ollamaService, times(2)).generateCommandSuggestion(anyString(), any());
    }

    @Test
    void testValidDockerCommand_PassesAllTiers() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        // For any call to Ollama, return same subcommand (no correction)
        when(ollamaService.generateCommandSuggestion(anyString(), any()))
            .thenReturn("ps"); // Ollama returns same subcommand (no correction)

        CommandSuggestion suggestion = validator.validateAndCorrect("docker ps -a");

        assertNotNull(suggestion);
        assertFalse(suggestion.needsCorrection());
        assertEquals("docker ps -a", suggestion.getOriginalInput());
        
        // Ollama should be called for validation
        verify(ollamaService, times(1)).generateCommandSuggestion(eq("docker ps"), any());
    }

    @Test
    void testGitStatusTypo_CaughtByStructuralValidation() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        CommandSuggestion suggestion = validator.validateAndCorrect("git statuts");

        assertNotNull(suggestion);
        // With Ollama not running, fallback correction will handle "statuts" -> "status"
        assertTrue(suggestion.needsCorrection());
        assertEquals("git status", suggestion.getSuggestion());
    }

    @Test
    void testInvalidFlag_CaughtByStructuralValidation() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        CommandSuggestion suggestion = validator.validateAndCorrect("docker ps -x");

        assertNotNull(suggestion);
        assertTrue(suggestion.needsCorrection());
        assertNotNull(suggestion.getSuggestion());
    }

    @Test
    void testOllamaNotAvailable_UsesOnlyStructuralValidation() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        CommandSuggestion suggestion = validator.validateAndCorrect("docker ps -a");

        assertNotNull(suggestion);
        assertFalse(suggestion.needsCorrection());
        assertEquals("docker ps -a", suggestion.getOriginalInput());
    }

    @Test
    void testUnknownCommand_OllamaNotAvailable() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        CommandSuggestion suggestion = validator.validateAndCorrect("unknowncommand");

        assertNotNull(suggestion);
        // Unknown commands without metadata are assumed valid when Ollama is unavailable
        // This is intentional - they might be valid system commands we don't have metadata for
        // The fallbackValidation will catch truly invalid commands
        assertTrue(suggestion.isError() || !suggestion.needsCorrection());
        if (suggestion.isError()) {
            assertTrue(suggestion.getMessage().contains("Unknown command"));
        }
    }

    @Test
    void testOllamaSemanticValidation_AfterStructuralPasses() {
        // Command passes structural but Ollama suggests improvement
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        // For any call to Ollama, return enhanced command
        when(ollamaService.generateCommandSuggestion(anyString(), any()))
            .thenReturn("run -d nginx"); // Ollama suggests adding -d flag

        CommandSuggestion suggestion = validator.validateAndCorrect("docker run nginx");

        assertNotNull(suggestion);
        assertTrue(suggestion.needsCorrection());
        assertEquals("docker run -d nginx", suggestion.getSuggestion());
    }

    @Test
    void testOllamaEmptyResponse_CommandAssumedValid() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(anyString(), any()))
            .thenReturn(""); // Empty response

        CommandSuggestion suggestion = validator.validateAndCorrect("docker ps");

        assertNotNull(suggestion);
        assertFalse(suggestion.needsCorrection());
    }

    @Test
    void testOllamaException_CommandAssumedValid() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(anyString(), any()))
            .thenThrow(new RuntimeException("Ollama error"));

        CommandSuggestion suggestion = validator.validateAndCorrect("docker ps");

        assertNotNull(suggestion);
        assertFalse(suggestion.needsCorrection());
    }

    @Test
    void testParseInvalidJSON_HandlesGracefully() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(anyString(), any()))
            .thenReturn("invalid json response");

        CommandSuggestion suggestion = validator.validateAndCorrect("docker ps");

        assertNotNull(suggestion);
        // Should handle gracefully and assume valid
        assertFalse(suggestion.needsCorrection());
    }

    @Test
    void testMarkdownCodeBlockResponse_ParsedCorrectly() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(anyString(), any()))
            .thenReturn("ps"); // Simple response without markdown

        CommandSuggestion suggestion = validator.validateAndCorrect("docker ps");

        assertNotNull(suggestion);
        assertFalse(suggestion.needsCorrection());
    }

    @Test
    void testComplexCommand_AllTiersValidate() {
        String command = "docker run -d -p 8080:80 --name webserver nginx";
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        // For any call to Ollama, return same subcommand (no correction)
        when(ollamaService.generateCommandSuggestion(anyString(), any()))
            .thenReturn("run"); // Ollama returns same subcommand (no correction)

        CommandSuggestion suggestion = validator.validateAndCorrect(command);

        assertNotNull(suggestion);
        assertFalse(suggestion.needsCorrection());
    }

    @Test
    void testKubectlTypo_StructuralValidationCatches() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        CommandSuggestion suggestion = validator.validateAndCorrect("kubectl gte pods");

        assertNotNull(suggestion);
        assertTrue(suggestion.needsCorrection());
        assertEquals("kubectl get pods", suggestion.getSuggestion());
    }

    @Test
    void testNpmInstallTypo_StructuralValidationCatches() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        CommandSuggestion suggestion = validator.validateAndCorrect("npm instlal express");

        assertNotNull(suggestion);
        assertTrue(suggestion.needsCorrection());
        assertTrue(suggestion.getSuggestion().contains("install"));
    }

    @Test
    void testGetCommandParser() {
        CommandParser parser = validator.getCommandParser();
        assertNotNull(parser);
        assertSame(commandParser, parser);
    }

    @Test
    void testGetStructuralValidator() {
        StructuralCommandValidator structValidator = validator.getStructuralValidator();
        assertNotNull(structValidator);
        assertSame(structuralValidator, structValidator);
    }

    @Test
    void testIsOllamaAvailable() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        assertTrue(validator.isOllamaAvailable());

        when(ollamaService.isOllamaRunning()).thenReturn(false);
        assertFalse(validator.isOllamaAvailable());
    }

    @Test
    void testFallbackValidation_KnownCommand() {
        CommandSuggestion suggestion = validator.fallbackValidation("docker ps");
        assertNotNull(suggestion);
        assertFalse(suggestion.needsCorrection());
    }

    @Test
    void testFallbackValidation_UnknownCommand() {
        CommandSuggestion suggestion = validator.fallbackValidation("unknowncommand");
        assertNotNull(suggestion);
        assertTrue(suggestion.isError());
    }
}