package com.smartcommands.model;

import java.util.List;
import java.util.Set;

/**
 * Defines the structure and validation rules for a command
 */
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
    
    public String getBaseCommand() {
        return baseCommand;
    }
    
    public void setBaseCommand(String baseCommand) {
        this.baseCommand = baseCommand;
    }
    
    public Set<String> getSubcommands() {
        return subcommands;
    }
    
    public void setSubcommands(Set<String> subcommands) {
        this.subcommands = subcommands;
    }
    
    public Set<String> getCommonFlags() {
        return commonFlags;
    }
    
    public void setCommonFlags(Set<String> commonFlags) {
        this.commonFlags = commonFlags;
    }
    
    public Set<String> getValidFlagCombinations() {
        return validFlagCombinations;
    }
    
    public void setValidFlagCombinations(Set<String> validFlagCombinations) {
        this.validFlagCombinations = validFlagCombinations;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getCommonUsagePatterns() {
        return commonUsagePatterns;
    }
    
    public void setCommonUsagePatterns(List<String> commonUsagePatterns) {
        this.commonUsagePatterns = commonUsagePatterns;
    }
    
    /**
     * Check if a subcommand is valid for this command
     */
    public boolean isValidSubcommand(String subcommand) {
        return subcommands == null || subcommands.isEmpty() || subcommands.contains(subcommand);
    }
    
    /**
     * Check if a flag is valid for this command
     */
    public boolean isValidFlag(String flag) {
        return commonFlags == null || commonFlags.isEmpty() || 
               commonFlags.contains(flag) || commonFlags.contains("--" + flag) ||
               commonFlags.contains("-" + flag);
    }
    
    /**
     * Check if a flag combination is valid
     */
    public boolean isValidFlagCombination(String combination) {
        return validFlagCombinations == null || validFlagCombinations.isEmpty() ||
               validFlagCombinations.contains(combination);
    }
}
