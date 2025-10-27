package com.smartcommands.repository;

import com.smartcommands.model.CommandMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for command metadata
 * In production, this could be backed by a database or configuration files
 */
@Repository
public class CommandMetadataRepository {
    private static final Logger logger = LoggerFactory.getLogger(CommandMetadataRepository.class);

    private final Map<String, CommandMetadata> commandMetadata;

    public CommandMetadataRepository() {
        this.commandMetadata = new HashMap<>();
        initializeDefaultMetadata();
    }

    /**
     * Initialize with commonly used commands
     */
    private void initializeDefaultMetadata() {
        // Docker commands
        addMetadata(CommandMetadata.builder()
            .baseCommand("docker")
            .description("Docker container management")
            .validSubcommands(Set.of(
                "ps", "run", "stop", "start", "restart", "rm", "rmi",
                "images", "exec", "logs", "build", "pull", "push",
                "inspect", "stats", "top", "attach", "commit", "cp",
                "create", "diff", "events", "export", "history",
                "import", "info", "kill", "load", "login", "logout",
                "pause", "port", "rename", "save", "search", "tag",
                "unpause", "update", "version", "wait", "network",
                "volume", "system", "compose", "container", "image"
            ))
            .validFlags(Set.of(
                "-a", "--all", "-q", "--quiet", "-s", "--size",
                "-f", "--filter", "-n", "--last", "-l", "--latest",
                "-v", "--volume", "-p", "--publish", "-d", "--detach",
                "-e", "--env", "-i", "--interactive", "-t", "--tty",
                "--rm", "--name", "--network", "-w", "--workdir",
                "-u", "--user", "-m", "--memory", "-c", "--cpu-shares",
                "--help", "--version", "--format", "--no-trunc"
            ))
            .build());

        // Git commands
        addMetadata(CommandMetadata.builder()
            .baseCommand("git")
            .description("Version control system")
            .validSubcommands(Set.of(
                "add", "commit", "push", "pull", "clone", "status",
                "log", "diff", "branch", "checkout", "merge", "rebase",
                "reset", "revert", "tag", "fetch", "remote", "init",
                "config", "stash", "show", "rm", "mv", "restore",
                "switch", "cherry-pick", "bisect", "grep", "blame"
            ))
            .validFlags(Set.of(
                "-m", "--message", "-a", "--all", "-v", "--verbose",
                "-f", "--force", "-b", "--branch", "-d", "--delete",
                "-u", "--set-upstream", "-p", "--patch", "-n", "--dry-run",
                "--amend", "--author", "--date", "--hard", "--soft",
                "--mixed", "--cached", "--help", "--version"
            ))
            .build());

        // Kubectl commands
        addMetadata(CommandMetadata.builder()
            .baseCommand("kubectl")
            .description("Kubernetes cluster management")
            .validSubcommands(Set.of(
                "get", "describe", "create", "delete", "apply", "logs",
                "exec", "run", "expose", "scale", "rollout", "set",
                "edit", "label", "annotate", "config", "cluster-info",
                "top", "cordon", "drain", "taint", "attach", "port-forward",
                "proxy", "cp", "auth", "diff", "patch", "replace", "wait"
            ))
            .validFlags(Set.of(
                "-f", "--filename", "-n", "--namespace", "-l", "--selector",
                "-o", "--output", "-w", "--watch", "--all-namespaces",
                "-v", "--verbose", "--dry-run", "--force", "--grace-period",
                "--help", "--context", "--kubeconfig", "--replicas"
            ))
            .build());

        // NPM commands
        addMetadata(CommandMetadata.builder()
            .baseCommand("npm")
            .description("Node package manager")
            .validSubcommands(Set.of(
                "install", "i", "uninstall", "update", "run", "start",
                "test", "build", "init", "publish", "version", "search",
                "view", "list", "ls", "link", "audit", "outdated",
                "cache", "config", "docs", "repo", "help"
            ))
            .validFlags(Set.of(
                "-g", "--global", "-D", "--save-dev", "-S", "--save",
                "-E", "--save-exact", "-P", "--save-prod",
                "--dry-run", "--force", "--legacy-peer-deps",
                "--production", "--only", "--verbose", "--silent",
                "--help", "--version"
            ))
            .build());

        logger.info("Initialized metadata for {} commands", commandMetadata.size());
    }

    /**
     * Add command metadata to repository
     */
    public void addMetadata(CommandMetadata metadata) {
        commandMetadata.put(metadata.getBaseCommand().toLowerCase(), metadata);
    }

    /**
     * Get metadata for a command
     */
    public Optional<CommandMetadata> getMetadata(String baseCommand) {
        return Optional.ofNullable(commandMetadata.get(baseCommand.toLowerCase()));
    }

    /**
     * Check if metadata exists for a command
     */
    public boolean hasMetadata(String baseCommand) {
        return commandMetadata.containsKey(baseCommand.toLowerCase());
    }

    /**
     * Get all registered commands
     */
    public Set<String> getAllCommands() {
        return Set.copyOf(commandMetadata.keySet());
    }

    /**
     * Get count of registered commands
     */
    public int getCommandCount() {
        return commandMetadata.size();
    }
}
