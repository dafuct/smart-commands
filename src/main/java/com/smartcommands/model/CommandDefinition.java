package com.smartcommands.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Setter
@Getter
public class CommandDefinition {
    private String baseCommand;
    private Set<String> subcommands;
    private Set<String> commonFlags;
    private Set<String> validFlagCombinations;
    private String description;
    private List<String> commonUsagePatterns;
    
    public CommandDefinition() {}
    
    public CommandDefinition(String baseCommand, Set<String> subcommands, 
                           Set<String> commonFlags, String description) {
        this.baseCommand = baseCommand;
        this.subcommands = subcommands;
        this.commonFlags = commonFlags;
        this.description = description;
    }

    public boolean isValidSubcommand(String subcommand) {
        return subcommands == null || subcommands.isEmpty() || subcommands.contains(subcommand);
    }
    
    public boolean isValidFlag(String flag) {
        return commonFlags == null || commonFlags.isEmpty() || 
               commonFlags.contains(flag) || commonFlags.contains("--" + flag) ||
               commonFlags.contains("-" + flag);
    }
    
    public boolean isValidFlagCombination(String combination) {
        return validFlagCombinations == null || validFlagCombinations.isEmpty() ||
               validFlagCombinations.contains(combination);
    }
}
