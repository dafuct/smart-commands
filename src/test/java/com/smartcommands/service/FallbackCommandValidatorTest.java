package com.smartcommands.service;

import com.smartcommands.model.CommandSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FallbackCommandValidatorTest {

    private FallbackCommandValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FallbackCommandValidator();
    }

    @Test
    @DisplayName("Should detect 'docker sp' as invalid and suggest 'docker ps'")
    void testDockerSpTypo() {
        CommandSuggestion result = validator.validate("docker sp -a");

        assertTrue(result.needsCorrection(), "Should detect 'sp' as incorrect");
        assertEquals("docker ps -a", result.getSuggestion());
        assertNotNull(result.getMessage());
    }

    @Test
    @DisplayName("Should validate 'docker ps -a' as correct")
    void testValidDockerPs() {
        CommandSuggestion result = validator.validate("docker ps -a");

        assertFalse(result.isError(), "Should not be an error");
        assertFalse(result.needsCorrection(), "Should not need correction");
        assertEquals(CommandSuggestion.SuggestionType.REGULAR, result.getType());
    }

    @Test
    @DisplayName("Should validate 'docker ps -a -q' as correct")
    void testValidDockerPsMultipleFlags() {
        CommandSuggestion result = validator.validate("docker ps -a -q");

        assertFalse(result.isError());
        assertFalse(result.needsCorrection());
    }

    @Test
    @DisplayName("Should detect 'docker iamges' typo and suggest 'docker images'")
    void testDockerImagesTypo() {
        CommandSuggestion result = validator.validate("docker iamges");

        assertTrue(result.needsCorrection());
        assertEquals("docker images", result.getSuggestion());
    }

    @Test
    @DisplayName("Should detect invalid docker subcommand 'xyz'")
    void testInvalidDockerSubcommand() {
        CommandSuggestion result = validator.validate("docker xyz");

        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("Invalid subcommand"));
    }

    @Test
    @DisplayName("Should validate 'git status' as correct")
    void testValidGitStatus() {
        CommandSuggestion result = validator.validate("git status");

        assertFalse(result.isError());
        assertFalse(result.needsCorrection());
    }

    @Test
    @DisplayName("Should correct 'git stauts' typo to 'git status'")
    void testGitStatusTypo() {
        CommandSuggestion result = validator.validate("git stauts");

        assertTrue(result.needsCorrection());
        assertEquals("git status", result.getSuggestion());
    }

    @Test
    @DisplayName("Should validate 'git commit -m \"message\"' as correct")
    void testValidGitCommit() {
        CommandSuggestion result = validator.validate("git commit -m \"test\"");

        assertFalse(result.isError());
        assertFalse(result.needsCorrection());
    }

    @Test
    @DisplayName("Should detect 'gti' base command typo and suggest 'git'")
    void testGitBaseCommandTypo() {
        CommandSuggestion result = validator.validate("gti status");

        assertTrue(result.needsCorrection());
        assertEquals("git status", result.getSuggestion());
    }

    @Test
    @DisplayName("Should detect 'dokcer' base command typo and suggest 'docker'")
    void testDockerBaseCommandTypo() {
        CommandSuggestion result = validator.validate("dokcer ps");

        assertTrue(result.needsCorrection());
        assertEquals("docker ps", result.getSuggestion());
    }

    @Test
    @DisplayName("Should validate common standalone commands")
    void testCommonStandaloneCommands() {
        String[] commands = {"ls", "pwd", "mkdir test", "grep pattern file", "cat file"};

        for (String cmd : commands) {
            CommandSuggestion result = validator.validate(cmd);
            assertFalse(result.isError(), "Command '" + cmd + "' should be valid");
        }
    }

    @Test
    @DisplayName("Should detect unknown command")
    void testUnknownCommand() {
        CommandSuggestion result = validator.validate("unknowncommand123");

        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("Unknown command"));
    }

    @Test
    @DisplayName("Should validate 'kubectl get pods' as correct")
    void testValidKubectl() {
        CommandSuggestion result = validator.validate("kubectl get pods");

        assertFalse(result.isError());
        assertFalse(result.needsCorrection());
    }

    @Test
    @DisplayName("Should detect invalid kubectl subcommand")
    void testInvalidKubectlSubcommand() {
        CommandSuggestion result = validator.validate("kubectl invalid");

        assertTrue(result.isError());
    }

    @Test
    @DisplayName("Should handle empty command")
    void testEmptyCommand() {
        CommandSuggestion result = validator.validate("");

        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("Should handle whitespace-only command")
    void testWhitespaceCommand() {
        CommandSuggestion result = validator.validate("   ");

        assertTrue(result.isError());
    }

    @Test
    @DisplayName("Should validate commands with common flags like -h and --help")
    void testCommonHelpFlags() {
        CommandSuggestion result1 = validator.validate("docker -h");
        CommandSuggestion result2 = validator.validate("git --help");

        assertFalse(result1.isError());
        assertFalse(result2.isError());
    }

    @Test
    @DisplayName("Should use Levenshtein distance to find similar subcommands")
    void testLevenshteinSimilarity() {
        // 'stat' is close to 'start' for docker
        CommandSuggestion result = validator.validate("docker stat container");

        // Should either correct to 'start' or 'stats'
        if (result.needsCorrection()) {
            assertTrue(result.getSuggestion().contains("start") ||
                      result.getSuggestion().contains("stats"));
        }
    }

    @Test
    @DisplayName("Should handle docker with base command only")
    void testDockerBaseOnly() {
        CommandSuggestion result = validator.validate("docker");

        // Should accept as valid (user might want version info)
        assertFalse(result.isError());
    }

    @Test
    @DisplayName("Should validate complex docker commands")
    void testComplexDockerCommand() {
        CommandSuggestion result = validator.validate("docker run -d -p 8080:80 --name web nginx");

        assertFalse(result.isError());
        assertFalse(result.needsCorrection());
    }
}
