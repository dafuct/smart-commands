# Smart Commands - AI-Powered Terminal Command Assistant

## Overview

Smart Commands is an intelligent terminal assistant that provides real-time command suggestions, corrections, and natural language command generation using Ollama AI. It combines conservative validation with powerful AI capabilities to enhance your command-line experience while maintaining full control over your system.

## Features

### 🤖 AI-Powered Command Suggestions
- **Natural Language Processing**: Type `sc "describe what you want to do"` to get AI-generated commands
- **Intelligent Error Correction**: Automatically detects and suggests corrections for common command typos
- **Contextual Help**: Provides helpful suggestions when commands are not found
- **Markdown-Free Output**: Clean, executable commands without formatting artifacts

### 🛡️ Robust & Conservative
- **Non-Intrusive**: Only validates very obvious typos, won't interfere with normal workflow
- **Graceful Fallbacks**: Works perfectly even when Ollama is unavailable
- **Timeout Handling**: Prevents hanging on slow AI responses

## Quick Start

### Installation
```bash
# Clone the repository
git clone https://github.com/your-repo/smart-commands.git
cd smart-commands

# Run the installer
./install.sh
```

### Usage
```bash
# Natural language commands
sc "find all java files modified in last 24 hours"

# Command correction (automatic)
docker ps -a  # Will be automatically corrected to "docker ps -a"

# Get help for unknown commands
lsl -la        # Will show suggestions for "ls"
```

## Configuration

The system automatically configures:
- **Ollama Integration**: Connects to local Ollama service
- **Smart Commands Server**: Starts background server on port 17020
- **Shell Integration**: Adds command validation to your shell
- **Auto-start Services**: Configures services to start automatically

## Requirements

- **Java 21+** (auto-installed if needed)
- **Ollama**: Downloaded and configured automatically
- **macOS/Linux**: Cross-platform compatibility

## Troubleshooting

If you encounter issues:

1. **Check Ollama Status**: `~/.smart-commands/scripts/smart-commands-server.sh status`
2. **View Logs**: `~/.smart-commands/scripts/smart-commands-server.sh logs`
3. **Restart Services**: `~/.smart-commands/scripts/smart-commands-server.sh restart`
4. **Manual Installation**: Run `./install.sh` for fresh setup

## Support

For issues and feature requests, please visit the GitHub repository or check the built-in help:
```bash
~/.smart-commands/scripts/smart-commands-server.sh --help
```

---

*Smart Commands enhances your terminal experience with AI-powered intelligence while maintaining your complete control.*