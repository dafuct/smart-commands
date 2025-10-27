package com.smartcommands.service;

import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.parser.CommandParser;
import com.smartcommands.repository.CommandHistoryRepository;
import com.smartcommands.repository.CommandMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests for CommandProcessorService
 * Tests the complete validation flow including circuit breaker and fallback mechanisms
 */
class CommandProcessorServiceIntegrationTest {

    @Mock
    private OllamaService ollamaService;

    @Mock
    private CommandHistoryRepository commandHistoryRepository;

    private IntelligentCommandValidator intelligentValidator;
    private CommandProcessorService commandProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Initialize three-tier validation architecture
        CommandParser commandParser = new CommandParser(ollamaService);
        CommandMetadataRepository metadataRepository = new CommandMetadataRepository();
        StructuralCommandValidator structuralValidator = 
            new StructuralCommandValidator(commandParser, metadataRepository);
        intelligentValidator = new IntelligentCommandValidator(
            ollamaService, commandParser, structuralValidator);

        commandProcessor = new CommandProcessorService(
            ollamaService,
            commandHistoryRepository,
            intelligentValidator
        );
    }

    @Test
    @DisplayName("Should process 'docker sp -a' through complete validation flow")
    void testCompleteFlowDockerSpCorrection() {
        // Mock Ollama as available and returning correction
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(eq("docker sp -a"), any()))
            .thenReturn("ps"); // Ollama returns corrected subcommand

        CommandSuggestion result = commandProcessor.processInput("docker sp -a");

        assertNotNull(result);
        assertTrue(result.needsCorrection());
        assertEquals("docker ps -a", result.getSuggestion());

        // Verify history was saved
        verify(commandHistoryRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should fall back to pattern validation when Ollama unavailable")
    void testFallbackWhenOllamaUnavailable() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        CommandSuggestion result = commandProcessor.processInput("docker sp -a");

        assertNotNull(result);
        // With Ollama unavailable, no correction will be made
        assertFalse(result.needsCorrection());

        verify(commandHistoryRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should activate circuit breaker after multiple Ollama failures")
    void testCircuitBreakerActivation() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(any(), any()))
            .thenThrow(new RuntimeException("Connection timeout"));

        // Trigger 3 failures to open circuit breaker
        for (int i = 0; i < 3; i++) {
            CommandSuggestion result = commandProcessor.processInput("docker sp -a");
            assertNotNull(result);
        }

        // Next call should use fallback directly without trying Ollama
        CommandSuggestion result = commandProcessor.processInput("docker sp -a");

        assertNotNull(result);
        // No correction expected when Ollama fails
        assertFalse(result.needsCorrection());

        // Ollama should have been called only 3 times (before circuit opened)
        verify(ollamaService, times(3)).generateCommandSuggestion(any(), any());
    }

    @Test
    @DisplayName("Should reset circuit breaker after successful call")
    void testCircuitBreakerReset() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);

        // First call fails
        when(ollamaService.generateCommandSuggestion(eq("docker sp"), any()))
            .thenThrow(new RuntimeException("Timeout"));
        CommandSuggestion result1 = commandProcessor.processInput("docker sp");
        assertNotNull(result1);

        // Second call succeeds - should reset circuit breaker
        when(ollamaService.generateCommandSuggestion(eq("docker ps -a"), any()))
            .thenReturn("ps"); // Ollama returns same subcommand
        CommandSuggestion result2 = commandProcessor.processInput("docker ps -a");

        assertNotNull(result2);
        assertFalse(result2.needsCorrection());

        // Circuit should be reset, allowing new calls
        verify(ollamaService, times(2)).generateCommandSuggestion(any(), any());
    }

    @Test
    @DisplayName("Should skip validation for pipes and redirections")
    void testSkipValidationForPipes() {
        CommandSuggestion result = commandProcessor.processInput("ls | grep test");

        assertNotNull(result);
        assertFalse(result.isError());

        // Should not attempt Ollama validation
        verify(ollamaService, never()).generateCommandSuggestion(any(), any());
    }

    @Test
    @DisplayName("Should skip validation for local scripts")
    void testSkipValidationForLocalScripts() {
        CommandSuggestion result = commandProcessor.processInput("./my-script.sh");

        assertNotNull(result);
        assertFalse(result.isError());

        verify(ollamaService, never()).generateCommandSuggestion(any(), any());
    }

    @Test
    @DisplayName("Should skip validation for environment variable assignments")
    void testSkipValidationForEnvVars() {
        CommandSuggestion result = commandProcessor.processInput("PATH=/usr/bin:$PATH");

        assertNotNull(result);
        assertFalse(result.isError());

        verify(ollamaService, never()).generateCommandSuggestion(any(), any());
    }

    @Test
    @DisplayName("Should process smart command request")
    void testSmartCommandRequest() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.suggestCommandsForTask("find large files"))
            .thenReturn("find . -type f -size +100M");

        CommandSuggestion result = commandProcessor.processInput("sc 'find large files'");

        assertNotNull(result);
        assertTrue(result.isSmartCommand());
        assertEquals("find . -type f -size +100M", result.getSuggestion());

        verify(commandHistoryRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should handle smart command when Ollama unavailable")
    void testSmartCommandOllamaUnavailable() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        CommandSuggestion result = commandProcessor.processInput("sc 'find large files'");

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("Ollama is not running"));
    }

    @Test
    @DisplayName("Should validate multiple docker subcommands correctly")
    void testMultipleDockerSubcommands() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);

        // Test various docker commands
        String[] validCommands = {"docker ps", "docker images", "docker run nginx", "docker stop container"};
        String[] invalidCommands = {"docker sp", "docker iamges", "docker strat"};

        for (String cmd : validCommands) {
            when(ollamaService.generateCommandSuggestion(eq(cmd), any()))
                .thenReturn("{\"type\":\"VALID\",\"message\":\"Command is correct\"}");

            CommandSuggestion result = commandProcessor.processInput(cmd);
            assertFalse(result.isError(), "Command should be valid: " + cmd);
        }

        for (String cmd : invalidCommands) {
            // Fallback will handle these
            CommandSuggestion result = commandProcessor.processInput(cmd);
            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("Should validate git commands with various subcommands")
    void testGitSubcommands() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);

        when(ollamaService.generateCommandSuggestion(eq("git status"), any()))
            .thenReturn("status"); // Ollama returns same subcommand

        CommandSuggestion result1 = commandProcessor.processInput("git status");
        assertFalse(result1.isError());

        when(ollamaService.generateCommandSuggestion(eq("git stauts"), any()))
            .thenReturn("status"); // Ollama corrects typo

        CommandSuggestion result2 = commandProcessor.processInput("git stauts");
        assertTrue(result2.needsCorrection());
        assertEquals("git status", result2.getSuggestion());
    }

    @Test
    @DisplayName("Should handle empty input gracefully")
    void testEmptyInput() {
        CommandSuggestion result = commandProcessor.processInput("");

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("Should handle null input gracefully")
    void testNullInput() {
        CommandSuggestion result = commandProcessor.processInput(null);

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("Should handle whitespace-only input")
    void testWhitespaceInput() {
        CommandSuggestion result = commandProcessor.processInput("   ");

        assertNotNull(result);
        assertTrue(result.isError());
    }

    @Test
    @DisplayName("Should handle unexpected exceptions gracefully")
    void testUnexpectedExceptionHandling() {
        when(ollamaService.isOllamaRunning()).thenThrow(new RuntimeException("Unexpected error"));

        CommandSuggestion result = commandProcessor.processInput("docker ps");

        assertNotNull(result);
        // Should fall back to pattern validation
    }

    @Test
    @DisplayName("Should correctly identify and preserve valid complex commands")
    void testComplexValidCommands() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(any(), any()))
            .thenReturn("{\"type\":\"VALID\",\"message\":\"Command is correct\"}");

        String[] complexCommands = {
            "docker run -d -p 8080:80 --name web nginx",
            "git commit -m \"Fix bug\" --amend",
            "kubectl get pods -n production -o wide"
        };

        for (String cmd : complexCommands) {
            CommandSuggestion result = commandProcessor.processInput(cmd);
            assertFalse(result.isError(), "Complex command should be valid: " + cmd);
        }
    }
}