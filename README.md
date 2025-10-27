# Smart Commands

🤖 **AI-powered CLI command assistant with auto-correction and generation**

Smart Commands is an intelligent terminal assistant that helps you write better shell commands by providing real-time suggestions, auto-corrections, and natural language command generation powered by Ollama AI.

## ✨ Features

- 🔍 **Real-time Command Validation** - Catches typos and suggests corrections before execution
- 🧠 **AI-Powered Suggestions** - Uses Ollama with qwen2.5-coder:3b model for intelligent command analysis
- 🗣️ **Natural Language Processing** - Convert plain English descriptions into shell commands
- 🔄 **Multi-Strategy Validation** - Structural, intelligent, and fallback validation methods
- 📚 **Command History** - Tracks and learns from your command patterns
- 🚀 **Auto-start Services** - Automatically starts on system boot (macOS/Linux)
- 🐚 **Shell Integration** - Seamless integration with zsh and other shells
- 📦 **Multiple Installation Methods** - Manual script, Homebrew, or direct download

## 🚀 Quick Start

### Option 1: Homebrew (Recommended)
```bash
brew tap your-username/smart-commands
brew install smart-commands
```

### Option 2: Manual Installation
```bash
curl -fsSL https://raw.githubusercontent.com/your-username/smart-commands/main/install.sh | bash
```

### Option 3: Direct Download
```bash
wget https://github.com/your-username/smart-commands/releases/latest/download/install.sh
chmod +x install.sh
./install.sh
```

## 📋 Requirements

- **Java 21+** (automatically installed locally)
- **Ollama** with qwen2.5-coder:3b model (automatically installed)
- **macOS or Linux** (Windows support planned)
- **zsh** shell (bash support planned)

## 🎯 Usage Examples

### Auto-correction
```bash
# Type a command with typos
$ docker sp -a
💡 Smart Commands Suggestion:
   Original:  docker sp -a
   Suggested: docker ps -a
📋 Command copied to clipboard!
Press Enter to execute, Ctrl+C to cancel

$ git comit -m "fix bug"
💡 Smart Commands Suggestion:
   Original:  git comit -m "fix bug"
   Suggested: git commit -m "fix bug"
```

### Natural Language Commands
```bash
# Describe what you want to do
$ sc 'find all java files in current directory'
💡 Smart Commands Suggestion:
   Original:  sc 'find all java files in current directory'
   Suggested: find . -name "*.java" -type f
📋 Command copied to clipboard!
Press Enter to execute, Ctrl+C to cancel

$ sc 'show system memory usage'
💡 Smart Commands Suggestion:
   Original:  sc 'show system memory usage'
   Suggested: free -h
```

### Server Management
```bash
# Start Smart Commands server
smart-commands-server start

# Check server status
smart-commands-server status

# View logs
smart-commands-server logs

# Stop server
smart-commands-server stop
```

## 🏗️ Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Shell CLI    │───▶│  Spring Boot    │───▶│   Ollama AI    │
│  Integration   │    │     Server      │    │    Service     │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│ Command Parser │    │  Command        │    │  qwen2.5-coder │
│ & Validator    │    │  Processor      │    │     Model       │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  H2 Database   │    │  REST API       │    │  Suggestions    │
│  (History)     │    │  (Port 17020)   │    │  & Corrections  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## ⚙️ Configuration

### Server Configuration
Edit `~/.smart-commands/config/application.properties`:

```properties
# Server port
server.port=17020

# Ollama configuration
ollama.base-url=http://localhost:11434/api
ollama.model=qwen2.5-coder:3b
ollama.timeout=30000

# Command processing
smartcommands.cache.enabled=true
smartcommands.max-history=1000
```

### Shell Integration
The shell integration is automatically added to `~/.zshrc`. Key features:

- **Command Interception**: Catches typos before execution
- **Smart Suggestions**: AI-powered command corrections
- **Natural Language**: `sc 'describe task'` for command generation
- **Clipboard Support**: Auto-copies suggestions to clipboard

## 🔧 Development

### Prerequisites
- Java 21+
- Gradle 7.0+
- Ollama with qwen2.5-coder:3b

### Build & Run
```bash
# Clone repository
git clone https://github.com/your-username/smart-commands.git
cd smart-commands

# Build application
./gradlew clean build

# Run server
./gradlew bootRun

# Run tests
./gradlew test
```

### Development Mode
```bash
# Start server in development mode
./gradlew bootRun --args='--spring.profiles.active=dev'

# Enable debug logging
./gradlew bootRun --args='--logging.level.com.smartcommands=DEBUG'
```

## 🧪 Testing

### Unit Tests
```bash
./gradlew test
```

### Integration Tests
```bash
./gradlew integrationTest
```

### Manual Testing
```bash
# Test shell integration
source ~/.smart-commands/scripts/smart-commands-shell.sh

# Test command correction
docker sp -a  # Should suggest: docker ps -a

# Test natural language
sc 'find big files'  # Should suggest: find . -size +100M
```

## 📚 API Documentation

### REST Endpoints

#### Command Suggestion
```http
POST /api/suggest
Content-Type: application/json

{
  "command": "docker sp -a"
}
```

#### Natural Language Command
```http
POST /api/smart-command
Content-Type: application/json

{
  "task": "find all java files"
}
```

#### Health Check
```http
GET /actuator/health
```

## 🔄 Auto-start Configuration

### macOS (launchd)
Services are configured in `~/Library/LaunchAgents/`:
- `com.ollama.ollama.plist` - Ollama service
- `com.smartcommands.server.plist` - Smart Commands server

### Linux (systemd)
Services are configured in `~/.config/systemd/user/`:
- `ollama.service` - Ollama service
- `smart-commands.service` - Smart Commands server

## 🗑️ Uninstallation

### Homebrew
```bash
brew uninstall smart-commands
brew untap your-username/smart-commands
```

### Manual
```bash
# Run uninstall script
./uninstall.sh

# Or manually remove
rm -rf ~/.smart-commands
# Remove shell integration from ~/.zshrc
```

## 🤝 Contributing

1. Fork repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

### Development Guidelines
- Follow Java code standards
- Add unit tests for new features
- Update documentation
- Ensure all tests pass

## 🐛 Troubleshooting

### Common Issues

#### Server Won't Start
```bash
# Check Java version
java -version  # Should be 21+

# Check logs
smart-commands-server logs

# Check port availability
lsof -i :17020
```

#### Ollama Not Responding
```bash
# Check Ollama status
ollama list

# Restart Ollama
ollama serve

# Check model
ollama pull qwen2.5-coder:3b
```

#### Shell Integration Not Working
```bash
# Reload shell configuration
source ~/.zshrc

# Check integration
grep "Smart Commands" ~/.zshrc

# Test manually
~/.smart-commands/scripts/smart-commands-shell.sh
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Ollama](https://ollama.com/) - AI model serving
- [qwen2.5-coder](https://ollama.com/library/qwen2.5-coder) - AI model for command analysis
- [Spring Boot](https://spring.io/projects/spring-boot) - Application framework
- [H2 Database](https://www.h2database.com/) - Embedded database

## 📊 Roadmap

- [ ] Windows support
- [ ] Bash shell support
- [ ] Additional AI models (GPT, Claude)
- [ ] Web dashboard
- [ ] Command aliases and shortcuts
- [ ] Team/shared configurations
- [ ] Plugin system
- [ ] Performance analytics

---

**Made with ❤️ for developers who love the command line**

[⭐ Star this repo](https://github.com/your-username/smart-commands) if it helped you write better commands!