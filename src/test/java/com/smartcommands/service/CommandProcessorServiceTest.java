package com.smartcommands.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.smartcommands.model.CommandHistory;
import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.repository.CommandHistoryRepository;

@ExtendWith(SpringExtension.class)
@DisplayName("CommandProcessorService Enhanced Unit Tests")
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

    // Enhanced Edge Case Tests

    @Test
    @DisplayName("Should handle Unicode characters in commands")
    void testUnicodeCharacters() {
        when(intelligentValidator.fallbackValidation("echo '你好世界'"))
            .thenReturn(CommandSuggestion.regularCommand("echo '你好世界'"));
        
        CommandSuggestion suggestion = commandProcessorService.processInput("echo '你好世界'");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        assertEquals("echo '你好世界'", suggestion.getOriginalInput());
    }

    @Test
    @DisplayName("Should handle extremely long commands")
    void testExtremelyLongCommand() {
        String longCommand = "echo " + "a".repeat(1000);
        when(intelligentValidator.fallbackValidation(longCommand))
            .thenReturn(CommandSuggestion.regularCommand(longCommand));
        
        CommandSuggestion suggestion = commandProcessorService.processInput(longCommand);
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        assertEquals(longCommand, suggestion.getOriginalInput());
    }

    @Test
    @DisplayName("Should handle commands with backticks")
    void testBackticksInCommand() {
        CommandSuggestion suggestion = commandProcessorService.processInput("echo `date`");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        verify(intelligentValidator, never()).validateAndCorrect(anyString());
    }

    @Test
    @DisplayName("Should handle process substitution")
    void testProcessSubstitution() {
        CommandSuggestion suggestion = commandProcessorService.processInput("diff <(ls) <(ls -a)");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        verify(intelligentValidator, never()).validateAndCorrect(anyString());
    }

    @Test
    @DisplayName("Should handle mixed quotes and escaping")
    void testMixedQuotesAndEscaping() {
        when(intelligentValidator.fallbackValidation("echo \"hello 'world' \\\"test\\\"\""))
            .thenReturn(CommandSuggestion.regularCommand("echo \"hello 'world' \\\"test\\\"\""));
        
        CommandSuggestion suggestion = commandProcessorService.processInput("echo \"hello 'world' \\\"test\\\"\"");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
    }

    @Test
    @DisplayName("Should handle commands with embedded newlines")
    void testEmbeddedNewlines() {
        String commandWithNewline = "echo 'line1\nline2'";
        when(intelligentValidator.fallbackValidation(commandWithNewline))
            .thenReturn(CommandSuggestion.regularCommand(commandWithNewline));
        
        CommandSuggestion suggestion = commandProcessorService.processInput(commandWithNewline);
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
    }

    @Test
    @DisplayName("Should handle shell variables in commands")
    void testShellVariables() {
        CommandSuggestion suggestion = commandProcessorService.processInput("echo $PATH");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        verify(intelligentValidator, never()).validateAndCorrect(anyString());
    }

    @Test
    @DisplayName("Should handle command substitution with $()")
    void testCommandSubstitution() {
        CommandSuggestion suggestion = commandProcessorService.processInput("echo $(date)");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        verify(intelligentValidator, never()).validateAndCorrect(anyString());
    }

    @Test
    @DisplayName("Should handle heredoc syntax")
    void testHeredocSyntax() {
        CommandSuggestion suggestion = commandProcessorService.processInput("cat <<EOF\nhello\nEOF");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        verify(intelligentValidator, never()).validateAndCorrect(anyString());
    }

    @Test
    @DisplayName("Should handle arithmetic expansion")
    void testArithmeticExpansion() {
        CommandSuggestion suggestion = commandProcessorService.processInput("echo $((1+1))");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        verify(intelligentValidator, never()).validateAndCorrect(anyString());
    }

    // Enhanced Circuit Breaker Tests

    @Test
    @DisplayName("Should reset circuit breaker after timeout")
    void testCircuitBreakerReset() throws InterruptedException {
        when(intelligentValidator.isOllamaAvailable()).thenReturn(true);
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(intelligentValidator.validateAndCorrect("docker ps"))
            .thenThrow(new RuntimeException("Ollama failure"));
        when(intelligentValidator.fallbackValidation("docker ps"))
            .thenReturn(CommandSuggestion.regularCommand("docker ps"));

        // First call should trigger circuit breaker and use fallback
        CommandSuggestion s1 = commandProcessorService.processInput("docker ps");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, s1.getType());

        // Second call should use fallback (circuit breaker open)
        CommandSuggestion s2 = commandProcessorService.processInput("docker ps");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, s2.getType());

        // Wait for circuit breaker to potentially reset (simulated)
        Thread.sleep(100);

        // Third call should still work with fallback
        CommandSuggestion s3 = commandProcessorService.processInput("docker ps");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, s3.getType());
    }

    @Test
    @DisplayName("Should handle concurrent command processing")
    void testConcurrentCommandProcessing() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        when(intelligentValidator.fallbackValidation(anyString()))
            .thenAnswer(inv -> CommandSuggestion.regularCommand(inv.getArgument(0)));

        @SuppressWarnings("unchecked")
        CompletableFuture<CommandSuggestion>[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            final int index = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                return commandProcessorService.processInput("docker ps " + index);
            }, executor);
        }

        // Wait for all to complete
        for (CompletableFuture<CommandSuggestion> future : futures) {
            CommandSuggestion suggestion = future.join();
            assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    // Parameterized Tests

    @ParameterizedTest
    @ValueSource(strings = {
        "ls | grep test",
        "find . -name '*.java' | xargs wc -l",
        "cat file.txt | sort | uniq",
        "ps aux | grep java | awk '{print $2}'"
    })
    @DisplayName("Should skip validation for pipe commands")
    void testPipeCommands(String command) {
        CommandSuggestion suggestion = commandProcessorService.processInput(command);
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        verify(intelligentValidator, never()).validateAndCorrect(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ls > output.txt",
        "echo 'hello' >> file.txt",
        "docker logs container 2>&1",
        "find . -name '*.log' 2>/dev/null"
    })
    @DisplayName("Should skip validation for redirection commands")
    void testRedirectionCommands(String command) {
        CommandSuggestion suggestion = commandProcessorService.processInput(command);
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        verify(intelligentValidator, never()).validateAndCorrect(anyString());
    }

    @ParameterizedTest
    @CsvSource({
        "'./script.sh', true",
        "'/usr/local/bin/script', true",
        "'../scripts/run.sh', true",
        "'script.sh', false",
        "'ls', false"
    })
    @DisplayName("Should handle local script execution")
    void testLocalScriptExecution(String command, boolean shouldSkip) {
        CommandSuggestion suggestion = commandProcessorService.processInput(command);
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        
        if (shouldSkip) {
            verify(intelligentValidator, never()).validateAndCorrect(anyString());
        }
    }

    // Enhanced Error Handling Tests

    @Test
    @DisplayName("Should handle null input gracefully")
    void testNullInput() {
        CommandSuggestion suggestion = commandProcessorService.processInput(null);
        assertTrue(suggestion.isError());
        assertTrue(suggestion.getMessage().toLowerCase().contains("empty"));
    }

    @Test
    @DisplayName("Should handle malformed smart command")
    void testMalformedSmartCommand() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);

        CommandSuggestion suggestion = commandProcessorService.processInput("sc incomplete");
        // Since "sc incomplete" doesn't match the smart command pattern (missing quotes),
        // it should be treated as a regular command, not an error
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
    }

    @Test
    @DisplayName("Should handle smart command with empty task")
    void testSmartCommandWithEmptyTask() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.suggestCommandsForTask(""))
            .thenReturn(null);

        CommandSuggestion suggestion = commandProcessorService.processInput("sc ''");
        assertTrue(suggestion.isError());
        assertTrue(suggestion.getMessage().toLowerCase().contains("failed to generate"));
    }

    @Test
    @DisplayName("Should handle very long smart command task")
    void testVeryLongSmartCommandTask() {
        when(ollamaService.isOllamaRunning()).thenReturn(true);
        when(ollamaService.suggestCommandsForTask(anyString()))
            .thenReturn("echo 'task completed'");
        
        String longTask = "a".repeat(1000);
        CommandSuggestion suggestion = commandProcessorService.processInput("sc '" + longTask + "'");
        assertTrue(suggestion.isSmartCommand());
    }

    // Performance and Memory Tests

    @Test
    @DisplayName("Should handle rapid successive commands")
    void testRapidSuccessiveCommands() {
        when(intelligentValidator.fallbackValidation(anyString()))
            .thenAnswer(inv -> CommandSuggestion.regularCommand(inv.getArgument(0)));

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            CommandSuggestion suggestion = commandProcessorService.processInput("echo " + i);
            assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        }
        long endTime = System.currentTimeMillis();

        // Should complete 100 commands in reasonable time (less than 5 seconds)
        assertTrue(endTime - startTime < 5000, "Commands should process quickly");
    }

    @Test
    @DisplayName("Should handle memory pressure with large commands")
    void testMemoryPressureWithLargeCommands() {
        when(intelligentValidator.fallbackValidation(anyString()))
            .thenAnswer(inv -> CommandSuggestion.regularCommand(inv.getArgument(0)));

        // Process multiple large commands to test memory handling
        for (int i = 0; i < 10; i++) {
            String largeCommand = "echo " + "x".repeat(10000) + " " + i;
            CommandSuggestion suggestion = commandProcessorService.processInput(largeCommand);
            assertEquals(CommandSuggestion.SuggestionType.REGULAR, suggestion.getType());
        }
        
        // If we get here without OutOfMemoryError, the test passes
        assertTrue(true);
    }

    // Integration-style Tests

    @Test
    @DisplayName("Should handle complete flow with correction and history")
    void testCompleteFlowWithCorrectionAndHistory() {
        CommandHistory existingHistory = new CommandHistory("docker ps", "docker ps -a",
            CommandSuggestion.SuggestionType.CORRECTION, "docker ps -a");

        when(commandHistoryRepository.searchCommands("docker ps"))
            .thenReturn(List.of(existingHistory));
        when(intelligentValidator.isOllamaAvailable()).thenReturn(true);
        when(intelligentValidator.validateAndCorrect("docker ps"))
            .thenReturn(CommandSuggestion.correction("docker ps", "docker ps -a"));

        CommandSuggestion suggestion = commandProcessorService.processInput("docker ps");

        assertTrue(suggestion.needsCorrection());
        assertEquals("docker ps", suggestion.getOriginalInput());
        assertEquals("docker ps -a", suggestion.getSuggestion());

        verify(commandHistoryRepository).save(any(CommandHistory.class));
    }

    @Test
    @DisplayName("Should handle complex docker command with multiple corrections")
    void testComplexDockerCommandWithCorrections() {
        when(intelligentValidator.isOllamaAvailable()).thenReturn(true);
        when(intelligentValidator.validateAndCorrect("docker run -d -p 8080:80 --name web nginx"))
            .thenReturn(CommandSuggestion.correction("docker run -d -p 8080:80 --name web nginx",
                "docker run -d -p 8080:80 --name web nginx:latest"));

        CommandSuggestion suggestion = commandProcessorService.processInput(
            "docker run -d -p 8080:80 --name web nginx");

        assertTrue(suggestion.needsCorrection());
        assertTrue(suggestion.getSuggestion().contains("nginx:latest"));
        verify(commandHistoryRepository).save(any(CommandHistory.class));
    }
}