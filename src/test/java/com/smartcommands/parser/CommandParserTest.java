package com.smartcommands.parser;

import com.smartcommands.model.CommandStructure;
import com.smartcommands.service.OllamaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CommandParserTest {

    private CommandParser parser;
    
    @Mock
    private OllamaService mockOllamaService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockOllamaService.isOllamaRunning()).thenReturn(false);
        parser = new CommandParser(mockOllamaService);
    }

    @Test
    void testParseSimpleCommand() {
        CommandStructure structure = parser.parse("ls");

        assertEquals("ls", structure.getBaseCommand());
        assertFalse(structure.hasSubcommand());
        assertFalse(structure.hasFlags());
        assertFalse(structure.hasArguments());
    }

    @Test
    void testParseCommandWithFlags() {
        CommandStructure structure = parser.parse("ls -la");

        assertEquals("ls", structure.getBaseCommand());
        assertFalse(structure.hasSubcommand());
        assertTrue(structure.hasFlags());
        assertEquals(1, structure.getFlags().size());
        assertEquals("-la", structure.getFlags().get(0));
    }

    @Test
    void testParseDockerPsCommand() {
        CommandStructure structure = parser.parse("docker ps -a");

        assertEquals("docker", structure.getBaseCommand());
        assertEquals("ps", structure.getSubcommand());
        assertTrue(structure.hasSubcommand());
        assertTrue(structure.hasFlags());
        assertEquals(1, structure.getFlags().size());
        assertEquals("-a", structure.getFlags().get(0));
    }

    @Test
    void testParseDockerSpCommand_InvalidSubcommand() {
        // Parser should still parse the structure, validation happens later
        CommandStructure structure = parser.parse("docker sp -a");

        assertEquals("docker", structure.getBaseCommand());
        assertEquals("sp", structure.getSubcommand());
        assertTrue(structure.hasSubcommand());
        assertTrue(structure.hasFlags());
        assertEquals("-a", structure.getFlags().get(0));
    }

    @Test
    void testParseGitCommitWithMessage() {
        CommandStructure structure = parser.parse("git commit -m 'Initial commit'");

        assertEquals("git", structure.getBaseCommand());
        assertEquals("commit", structure.getSubcommand());
        assertTrue(structure.hasFlags());
        assertEquals("-m", structure.getFlags().get(0));
        assertTrue(structure.hasArguments());
        assertEquals("Initial commit", structure.getArguments().get(0));
    }

    @Test
    void testParseCommandWithMultipleFlags() {
        CommandStructure structure = parser.parse("docker ps -a -q -s");

        assertEquals("docker", structure.getBaseCommand());
        assertEquals("ps", structure.getSubcommand());
        assertEquals(3, structure.getFlags().size());
        assertTrue(structure.getFlags().contains("-a"));
        assertTrue(structure.getFlags().contains("-q"));
        assertTrue(structure.getFlags().contains("-s"));
    }

    @Test
    void testParseCommandWithLongFlags() {
        CommandStructure structure = parser.parse("docker ps --all --quiet");

        assertEquals("docker", structure.getBaseCommand());
        assertEquals("ps", structure.getSubcommand());
        assertEquals(2, structure.getFlags().size());
        assertEquals("--all", structure.getFlags().get(0));
        assertEquals("--quiet", structure.getFlags().get(1));
    }

    @Test
    void testParseCommandWithDoubleQuotes() {
        CommandStructure structure = parser.parse("git commit -m \"Test message\"");

        assertEquals("git", structure.getBaseCommand());
        assertEquals("commit", structure.getSubcommand());
        assertEquals("-m", structure.getFlags().getFirst());
        assertEquals("Test message", structure.getArguments().getFirst());
    }

    @Test
    void testParseKubectlGetPods() {
        CommandStructure structure = parser.parse("kubectl get pods -n default");

        assertEquals("kubectl", structure.getBaseCommand());
        assertEquals("get", structure.getSubcommand());
        assertEquals(1, structure.getFlags().size());
        assertEquals("-n", structure.getFlags().getFirst());
        assertEquals(2, structure.getArguments().size());
        assertEquals("pods", structure.getArguments().get(0));
        assertEquals("default", structure.getArguments().get(1));
    }

    @Test
    void testReconstruct_SimpleCommand() {
        CommandStructure structure = parser.parse("docker ps -a");
        String reconstructed = structure.reconstruct();

        assertEquals("docker ps -a", reconstructed);
    }

    @Test
    void testReconstruct_WithQuotedArgument() {
        CommandStructure structure = parser.parse("git commit -m 'test message'");
        String reconstructed = structure.reconstruct();

        assertEquals("git commit -m 'test message'", reconstructed);
    }

    @ParameterizedTest
    @CsvSource({
        "docker, true",
        "git, true",
        "kubectl, true",
        "npm, true",
        "ls, false",
        "cd, false",
        "echo, false"
    })
    void testExpectsSubcommand(String command, boolean expected) {
        assertEquals(expected, parser.expectsSubcommand(command));
    }

    @Test
    void testParseNullCommand_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    void testParseEmptyCommand_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
    }

    @Test
    void testParseWhitespaceCommand_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
    }

    @Test
    void testParseCommandWithFlagEquals() {
        CommandStructure structure = parser.parse("docker run --name=myapp");

        assertEquals("docker", structure.getBaseCommand());
        assertEquals("run", structure.getSubcommand());
        assertTrue(structure.hasFlags());
        assertEquals("--name=myapp", structure.getFlags().getFirst());
    }

    @Test
    void testParseComplexDockerCommand() {
        String command = "docker run -d -p 8080:80 --name webserver nginx";
        CommandStructure structure = parser.parse(command);

        assertEquals("docker", structure.getBaseCommand());
        assertEquals("run", structure.getSubcommand());
        assertEquals(3, structure.getFlags().size());
        assertTrue(structure.getFlags().contains("-d"));
        assertTrue(structure.getFlags().contains("-p"));
        assertTrue(structure.getFlags().contains("--name"));
        assertTrue(structure.hasArguments());
    }
}