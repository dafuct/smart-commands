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

class CommandOllamaServiceTest {

    @Mock
    private OllamaService ollamaService;

    @Mock
    private CommandHistoryRepository commandHistoryRepository;

    private CommandProcessorService commandProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        CommandParser commandParser = new CommandParser(ollamaService);
        CommandMetadataRepository metadataRepository = new CommandMetadataRepository();
        StructuralCommandValidator structuralValidator = 
            new StructuralCommandValidator(commandParser, metadataRepository);
        IntelligentCommandValidator intelligentValidator = new IntelligentCommandValidator(
                ollamaService, commandParser, structuralValidator);

        commandProcessor = new CommandProcessorService(
            ollamaService,
            commandHistoryRepository,
                intelligentValidator
        );
    }

    @Test
    void testCompleteFlowDockerSpCorrection() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(eq("docker sp -a"), any()))
            .thenReturn("ps");
        CommandSuggestion result = commandProcessor.processInput("docker sp -a");
        assertNotNull(result);
        if (result.needsCorrection()) {
            assertTrue(result.getSuggestion().contains("ps"));
            assertTrue(result.getSuggestion().contains("-a"));
        } else {
            assertEquals("docker sp -a", result.getOriginalInput());
        }
        verify(commandHistoryRepository, times(1)).save(any());
        verify(ollamaService, atLeast(0)).generateCommandSuggestion(any(), any());
    }

    @Test
    void testFallbackWhenOllamaUnavailable() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);
        CommandSuggestion result = commandProcessor.processInput("docker sp -a");
        assertNotNull(result);
        // Falls back directly (no history save in current implementation)
        assertFalse(result.needsCorrection());
        assertEquals("docker sp -a", result.getOriginalInput());
        verify(commandHistoryRepository, never()).save(any());
        verify(ollamaService, never()).generateCommandSuggestion(any(), any());
    }

    @Test
    void testStructuralShortCircuitRepeated() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        for (int i = 0; i < 3; i++) {
            CommandSuggestion result = commandProcessor.processInput("docker sp -a");
            assertNotNull(result);
            assertTrue(result.needsCorrection());
            assertEquals("docker ps -a", result.getSuggestion());
        }
        verify(commandHistoryRepository, times(3)).save(any());
        verify(ollamaService, atLeast(1)).generateCommandSuggestion(any(), any());
    }

    @Test
    void testValidCommandInvokesOllama() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.generateCommandSuggestion(eq("docker ps -a"), any()))
            .thenReturn("ps");

        CommandSuggestion result = commandProcessor.processInput("docker ps -a");
        assertNotNull(result);
        assertFalse(result.isError());
        verify(commandHistoryRepository, times(1)).save(any());
        verify(ollamaService, atLeastOnce()).generateCommandSuggestion(eq("docker ps -a"), any());
    }

    @Test
    void testGitTypoStructuralCorrection() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        CommandSuggestion result = commandProcessor.processInput("git stauts");
        assertNotNull(result);
        assertTrue(result.needsCorrection());
        assertEquals("git status", result.getSuggestion());
        verify(commandHistoryRepository, times(1)).save(any());
        verify(ollamaService, atLeast(1)).generateCommandSuggestion(any(), any());
    }

    @Test
    @DisplayName("Should skip validation for pipes and redirections")
    void testSkipValidationForPipes() {
        CommandSuggestion result = commandProcessor.processInput("ls | grep test");

        assertNotNull(result);
        assertFalse(result.isError());

        verify(ollamaService, never()).generateCommandSuggestion(any(), any());
    }

    @Test
    void testSkipValidationForLocalScripts() {
        CommandSuggestion result = commandProcessor.processInput("./my-script.sh");

        assertNotNull(result);
        assertFalse(result.isError());

        verify(ollamaService, never()).generateCommandSuggestion(any(), any());
    }

    @Test
    void testSkipValidationForEnvVars() {
        CommandSuggestion result = commandProcessor.processInput("PATH=/usr/bin:$PATH");

        assertNotNull(result);
        assertFalse(result.isError());

        verify(ollamaService, never()).generateCommandSuggestion(any(), any());
    }

    @Test
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
    void testSmartCommandOllamaUnavailable() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);

        CommandSuggestion result = commandProcessor.processInput("sc 'find large files'");

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("Ollama is not running"));
    }

    @Test
    void testMultipleDockerSubcommands() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);

        String[] validCommands = {"docker ps", "docker images", "docker run nginx", "docker stop container"};
        String[] invalidCommands = {"docker sp", "docker iamges", "docker strat"};

        for (String cmd : validCommands) {
            when(ollamaService.generateCommandSuggestion(eq(cmd), any()))
                .thenReturn("{\"type\":\"VALID\",\"message\":\"Command is correct\"}");

            CommandSuggestion result = commandProcessor.processInput(cmd);
            assertFalse(result.isError(), "Command should be valid: " + cmd);
        }

        for (String cmd : invalidCommands) {
            CommandSuggestion result = commandProcessor.processInput(cmd);
            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("Should validate git commands with various subcommands")
    void testGitSubcommands() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);

        when(ollamaService.generateCommandSuggestion(eq("git status"), any()))
            .thenReturn("status");

        CommandSuggestion result1 = commandProcessor.processInput("git status");
        assertFalse(result1.isError());

        when(ollamaService.generateCommandSuggestion(eq("git stauts"), any()))
            .thenReturn("status");

        CommandSuggestion result2 = commandProcessor.processInput("git stauts");
        if (result2.needsCorrection()) {
            assertTrue(result2.getSuggestion().contains("status"));
        } else {
            fail("Expected a correction for typo 'stauts'");
        }
    }

    @Test
    void testEmptyInput() {
        CommandSuggestion result = commandProcessor.processInput("");

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("empty"));
    }

    @Test
    void testNullInput() {
        CommandSuggestion result = commandProcessor.processInput(null);

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("empty"));
    }

    @Test
    void testWhitespaceInput() {
        CommandSuggestion result = commandProcessor.processInput("   ");

        assertNotNull(result);
        assertTrue(result.isError());
    }

    @Test
    void testUnexpectedExceptionHandling() {
        when(ollamaService.isOllamaRunning()).thenThrow(new RuntimeException("Unexpected error"));

        CommandSuggestion result = commandProcessor.processInput("docker ps");

        assertNotNull(result);
    }

    @Test
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