package com.smartcommands.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.smartcommands.model.CommandHistory;
import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.repository.CommandHistoryRepository;

@ExtendWith(SpringExtension.class)
class CommandProcessorServiceTest {
    @Mock private CommandHistoryRepository commandHistoryRepository;
    @Mock private OllamaService ollamaService;
    @Mock private IntelligentCommandValidator intelligentValidator;
    @InjectMocks private CommandProcessorService commandProcessorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        commandProcessorService = new CommandProcessorService(ollamaService, commandHistoryRepository, intelligentValidator);
        when(ollamaService.isOllamaRunning()).thenReturn(false);
        when(intelligentValidator.isOllamaAvailable()).thenReturn(false);
        when(intelligentValidator.fallbackValidation(anyString()))
            .thenAnswer(inv -> CommandSuggestion.regularCommand(inv.getArgument(0)));
        when(intelligentValidator.validateAndCorrect(anyString()))
            .thenAnswer(inv -> CommandSuggestion.regularCommand(inv.getArgument(0)));
    }

    @Test
    void testRegularCommand() {
        when(intelligentValidator.fallbackValidation("ls"))
            .thenReturn(CommandSuggestion.regularCommand("ls"));
        CommandSuggestion suggestion = commandProcessorService.processInput("ls");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        assertEquals("ls", suggestion.getOriginalInput());
        assertNull(suggestion.getSuggestion());
        verify(intelligentValidator).fallbackValidation("ls");
    }

    @Test
    void testIncorrectCommandDetection() {
        when(intelligentValidator.fallbackValidation("lss"))
            .thenReturn(CommandSuggestion.correction("lss", "ls"));
        CommandSuggestion suggestion = commandProcessorService.processInput("lss");
        assertTrue(suggestion.needsCorrection());
        assertEquals("lss", suggestion.getOriginalInput());
        assertEquals("ls", suggestion.getSuggestion());
    }

    @Test
    void testSmartCommandProcessing() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(intelligentValidator.isOllamaAvailable()).thenReturn(true);
        when(ollamaService.suggestCommandsForTask("find big file"))
            .thenReturn("find / -type f -size +100M");
        CommandSuggestion suggestion = commandProcessorService.processInput("sc 'find big file'");
        assertTrue(suggestion.isSmartCommand());
        assertEquals("find / -type f -size +100M", suggestion.getSuggestion());
    }

    @Test
    void testSmartCommandProcessing_OllamaDown() {
        when(ollamaService.isOllamaRunning()).thenReturn(false);
        CommandSuggestion suggestion = commandProcessorService.processInput("sc 'find big file'");
        assertTrue(suggestion.isError());
        assertNotNull(suggestion.getMessage());
        assertTrue(suggestion.getMessage().toLowerCase().contains("ollama"));
    }

    @Test
    void testEmptyCommand() {
        CommandSuggestion suggestion = commandProcessorService.processInput("   ");
        assertTrue(suggestion.isError());
    }

    @Test
    void testMethodReferencing() {
        CommandHistory history = new CommandHistory("ls", "ls", CommandSuggestion.SuggestionType.REGULAR, "ls");
        when(commandHistoryRepository.searchCommands("ls")).thenReturn(List.of(history));
        when(intelligentValidator.fallbackValidation("ls"))
            .thenReturn(CommandSuggestion.regularCommand("ls"));
        Optional<CommandHistory> found = commandProcessorService.findCommandInDatabase("ls");
        assertTrue(found.isPresent());
        CommandSuggestion suggestion = commandProcessorService.processInput("ls");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
    }

    @Test
    void testCircuitBreakerFallback() {
        when(intelligentValidator.isOllamaAvailable()).thenReturn(true);
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(intelligentValidator.validateAndCorrect("docker ps"))
            .thenThrow(new RuntimeException("Ollama failure"));
        when(intelligentValidator.fallbackValidation("docker ps"))
            .thenReturn(CommandSuggestion.regularCommand("docker ps"));
        CommandSuggestion s1 = commandProcessorService.processInput("docker ps");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, s1.getType());
        CommandSuggestion s2 = commandProcessorService.processInput("docker ps");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, s2.getType());
    }

    @Test
    void testSkipValidationForComment() {
        CommandSuggestion suggestion = commandProcessorService.processInput("# just a comment");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
    }

    @Test
    void testSkipValidationForEnvAssignment() {
        CommandSuggestion suggestion = commandProcessorService.processInput("MY_VAR=123");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
    }
}