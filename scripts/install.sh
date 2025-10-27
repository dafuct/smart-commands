#!/bin/bash

################################################################################
# Smart Commands Installation Script
#
# This script installs Smart Commands CLI tool with the following steps:
# 1. Creates installation directory structure in ~/.smart-commands
# 2. Checks/Installs Java 21 (required for Spring Boot)
# 3. Checks/Installs Ollama AI service
# 4. Downloads qwen2.5-coder:3b model
# 5. Builds the application JAR
# 6. Sets up server management scripts
# 7. Integrates with .zshrc for shell command interception
################################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Installation configuration
INSTALL_DIR="$HOME/.smart-commands"
BIN_DIR="$INSTALL_DIR/bin"
DATA_DIR="$INSTALL_DIR/data"
LOGS_DIR="$INSTALL_DIR/logs"
SCRIPTS_DIR="$INSTALL_DIR/scripts"
CONFIG_DIR="$INSTALL_DIR/config"
JDK_DIR="$INSTALL_DIR/jdk"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Application configuration
APP_NAME="Smart Commands"
APP_VERSION="1.0.0"
SERVER_PORT="17020"
OLLAMA_MODEL="qwen2.5-coder:3b"
JAVA_VERSION="21"

################################################################################
# Utility Functions
################################################################################

print_header() {
    echo
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}  $1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo
}

print_step() {
    echo -e "${BLUE}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${MAGENTA}ℹ $1${NC}"
}

# Check if command exists
command_exists() {
    command -v "$1" &> /dev/null
}

# Detect OS
detect_os() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "macos"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        echo "linux"
    else
        echo "unknown"
    fi
}

################################################################################
# Installation Steps
################################################################################

# Step 1: Create Directory Structure
create_directories() {
    print_step "Creating installation directory structure..."

    mkdir -p "$INSTALL_DIR"
    mkdir -p "$BIN_DIR"
    mkdir -p "$DATA_DIR"
    mkdir -p "$LOGS_DIR"
    mkdir -p "$SCRIPTS_DIR"
    mkdir -p "$CONFIG_DIR"
    mkdir -p "$JDK_DIR"

    print_success "Directories created at $INSTALL_DIR"
}

# Step 2: Check Prerequisites
check_prerequisites() {
    print_step "Checking prerequisites..."

    local missing_deps=()

    if ! command_exists curl; then
        missing_deps+=("curl")
    fi

    if ! command_exists git; then
        missing_deps+=("git")
    fi

    if [ ${#missing_deps[@]} -ne 0 ]; then
        print_error "Missing required dependencies: ${missing_deps[*]}"
        print_info "Please install: ${missing_deps[*]}"
        exit 1
    fi

    print_success "All prerequisites satisfied"
}

# Step 3: Install Local Java (No Global Installation)
install_java() {
    print_step "Checking local Java installation..."

    local java_bin="$JDK_DIR/bin/java"

    # Check if local Java already exists
    if [ -f "$java_bin" ]; then
        local java_version=$("$java_bin" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)

        if [ "$java_version" -ge "$JAVA_VERSION" ]; then
            print_success "Local Java $java_version is already installed at $JDK_DIR"
            export JAVA_HOME="$JDK_DIR"
            export PATH="$JDK_DIR/bin:$PATH"
            return 0
        else
            print_warning "Found Java $java_version, but need Java $JAVA_VERSION+"
            print_step "Removing old Java installation..."
            rm -rf "$JDK_DIR"
            mkdir -p "$JDK_DIR"
        fi
    fi

    print_step "Installing Java $JAVA_VERSION locally (no global installation)..."
    print_info "This will download ~200MB and install only for this project"

    local os=$(detect_os)
    local arch=$(uname -m)
    local download_url=""
    local jdk_archive=""

    # Determine architecture
    case "$arch" in
        x86_64|amd64)
            arch="x64"
            ;;
        arm64|aarch64)
            arch="aarch64"
            ;;
        *)
            print_error "Unsupported architecture: $arch"
            exit 1
            ;;
    esac

    # Get download URL for Eclipse Temurin (Adoptium) JDK 21
    case "$os" in
        macos)
            if [ "$arch" = "aarch64" ]; then
                download_url="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.5_11.tar.gz"
                jdk_archive="OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.5_11.tar.gz"
            else
                download_url="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_mac_hotspot_21.0.5_11.tar.gz"
                jdk_archive="OpenJDK21U-jdk_x64_mac_hotspot_21.0.5_11.tar.gz"
            fi
            ;;

        linux)
            if [ "$arch" = "aarch64" ]; then
                download_url="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.5_11.tar.gz"
                jdk_archive="OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.5_11.tar.gz"
            else
                download_url="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_linux_hotspot_21.0.5_11.tar.gz"
                jdk_archive="OpenJDK21U-jdk_x64_linux_hotspot_21.0.5_11.tar.gz"
            fi
            ;;

        *)
            print_error "Unsupported OS: $os"
            exit 1
            ;;
    esac

    # Download JDK
    print_step "Downloading JDK from Adoptium..."
    local temp_dir=$(mktemp -d)
    cd "$temp_dir"

    if curl -L -o "$jdk_archive" "$download_url"; then
        print_success "Download complete"
    else
        print_error "Failed to download JDK"
        rm -rf "$temp_dir"
        exit 1
    fi

    # Extract JDK
    print_step "Extracting JDK to $JDK_DIR..."
    tar -xzf "$jdk_archive"

    # Find the extracted directory (it will be like jdk-21.0.5+11)
    local extracted_dir=$(find . -maxdepth 1 -type d -name "jdk-*" | head -1)

    if [ -z "$extracted_dir" ]; then
        print_error "Failed to find extracted JDK directory"
        rm -rf "$temp_dir"
        exit 1
    fi

    # Move contents to JDK_DIR
    # On macOS, JDK is in extracted_dir/Contents/Home
    # On Linux, JDK is directly in extracted_dir
    if [ "$os" = "macos" ]; then
        if [ -d "$extracted_dir/Contents/Home" ]; then
            mv "$extracted_dir/Contents/Home"/* "$JDK_DIR/"
        else
            mv "$extracted_dir"/* "$JDK_DIR/"
        fi
    else
        mv "$extracted_dir"/* "$JDK_DIR/"
    fi

    # Cleanup
    cd - > /dev/null
    rm -rf "$temp_dir"

    # Verify installation
    if [ -f "$java_bin" ]; then
        export JAVA_HOME="$JDK_DIR"
        export PATH="$JDK_DIR/bin:$PATH"
        local new_java_version=$("$java_bin" -version 2>&1 | head -n 1 | cut -d'"' -f2)
        print_success "Local Java $new_java_version installed successfully at $JDK_DIR"
        print_info "Java is installed ONLY for this project (not globally)"
    else
        print_error "Java installation failed"
        exit 1
    fi
}

# Step 4: Install/Check Ollama
install_ollama() {
    print_step "Checking Ollama installation..."

    if command_exists ollama; then
        print_success "Ollama is already installed"

        # Check if Ollama service is running
        if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
            print_success "Ollama service is running"
        else
            print_warning "Ollama is installed but not running"
            print_step "Starting Ollama service..."

            local os=$(detect_os)
            if [ "$os" = "macos" ]; then
                # On macOS, Ollama runs as an app
                open -a Ollama 2>/dev/null || print_warning "Please start Ollama manually"
            else
                # On Linux, start as service
                ollama serve > /dev/null 2>&1 &
                sleep 2
            fi
        fi
    else
        print_step "Installing Ollama..."

        curl -fsSL https://ollama.com/install.sh | sh

        print_success "Ollama installed successfully"

        # Start Ollama service
        print_step "Starting Ollama service..."
        local os=$(detect_os)
        if [ "$os" = "macos" ]; then
            open -a Ollama 2>/dev/null || print_warning "Please start Ollama manually"
        else
            ollama serve > /dev/null 2>&1 &
        fi

        sleep 3
    fi
}

# Step 5: Download Ollama Model
download_ollama_model() {
    print_step "Checking Ollama model: $OLLAMA_MODEL..."

    # Check if model is already downloaded
    if ollama list | grep -q "$OLLAMA_MODEL" 2>/dev/null; then
        print_success "Model $OLLAMA_MODEL is already downloaded"
        return 0
    fi

    print_step "Downloading model: $OLLAMA_MODEL..."
    print_info "This may take several minutes depending on your internet connection..."

    if ollama pull "$OLLAMA_MODEL"; then
        print_success "Model $OLLAMA_MODEL downloaded successfully"
    else
        print_error "Failed to download model $OLLAMA_MODEL"
        print_warning "You can download it later by running: ollama pull $OLLAMA_MODEL"
    fi
}

# Step 6: Build Application
build_application() {
    print_step "Building Smart Commands application..."

    cd "$PROJECT_DIR"

    # Check if gradlew exists
    if [ ! -f "./gradlew" ]; then
        print_error "Gradle wrapper not found in $PROJECT_DIR"
        exit 1
    fi

    # Make gradlew executable
    chmod +x ./gradlew

    # Set JAVA_HOME to use local Java
    export JAVA_HOME="$JDK_DIR"
    export PATH="$JDK_DIR/bin:$PATH"

    print_info "Using local Java at: $JAVA_HOME"

    # Build the application
    print_step "Running Gradle build..."
    ./gradlew clean bootJar

    # Check if JAR was created
    if [ ! -f "build/libs/smart-commands.jar" ]; then
        print_error "Build failed: JAR file not found"
        exit 1
    fi

    # Copy JAR to installation directory
    cp build/libs/smart-commands.jar "$BIN_DIR/"

    print_success "Application built and copied to $BIN_DIR"
}

# Step 7: Create Server Management Script
create_server_script() {
    print_step "Creating server management script..."

    cat > "$SCRIPTS_DIR/smart-commands-server.sh" << 'EOF'
#!/bin/bash

# Smart Commands Server Management Script

INSTALL_DIR="$HOME/.smart-commands"
BIN_DIR="$INSTALL_DIR/bin"
LOGS_DIR="$INSTALL_DIR/logs"
DATA_DIR="$INSTALL_DIR/data"
JDK_DIR="$INSTALL_DIR/jdk"
PID_FILE="$INSTALL_DIR/smart-commands.pid"
JAR_FILE="$BIN_DIR/smart-commands.jar"
LOG_FILE="$LOGS_DIR/smart-commands.log"
SERVER_PORT="17020"

# Use local Java installation
JAVA_BIN="$JDK_DIR/bin/java"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Check if server is running
is_running() {
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE")
        if ps -p "$pid" > /dev/null 2>&1; then
            return 0
        else
            rm -f "$PID_FILE"
            return 1
        fi
    fi
    return 1
}

# Start server
start() {
    if is_running; then
        echo -e "${YELLOW}Server is already running${NC}"
        return 0
    fi

    echo -e "${BLUE}Starting Smart Commands server...${NC}"

    # Check if local Java exists
    if [ ! -f "$JAVA_BIN" ]; then
        echo -e "${RED}✗ Local Java not found at $JAVA_BIN${NC}"
        echo -e "${YELLOW}Please run the installation script again${NC}"
        return 1
    fi

    # Create data directory if not exists
    mkdir -p "$DATA_DIR"
    mkdir -p "$LOGS_DIR"

    # Start server in background using local Java
    nohup "$JAVA_BIN" -jar "$JAR_FILE" \
        --server.port="$SERVER_PORT" \
        --spring.datasource.url="jdbc:h2:file:$DATA_DIR/smart-commands;DB_CLOSE_ON_EXIT=FALSE" \
        > "$LOG_FILE" 2>&1 &

    local pid=$!
    echo $pid > "$PID_FILE"

    # Wait for server to start with retries
    echo -e "${BLUE}Waiting for server to start...${NC}"
    local max_wait=30
    local count=0
    local server_ready=false

    while [ $count -lt $max_wait ]; do
        sleep 1
        count=$((count + 1))

        # Check if process is still running
        if ! is_running; then
            echo -e "${RED}✗ Server process died${NC}"
            echo -e "${YELLOW}Check logs: $LOG_FILE${NC}"
            return 1
        fi

        # Check if server is responding (port is open)
        if curl -s "http://localhost:$SERVER_PORT/actuator/health" > /dev/null 2>&1; then
            server_ready=true
            break
        fi

        if [ $((count % 5)) -eq 0 ]; then
            echo -e "${BLUE}Still waiting... ($count seconds)${NC}"
        fi
    done

    if [ "$server_ready" = true ]; then
        echo -e "${GREEN}✓ Server started successfully (PID: $pid)${NC}"
        echo -e "${BLUE}Server is listening on port $SERVER_PORT${NC}"
        echo -e "${BLUE}Logs: $LOG_FILE${NC}"
    else
        echo -e "${YELLOW}⚠ Server process is running but not responding yet${NC}"
        echo -e "${YELLOW}This might be normal on first start. Check logs: $LOG_FILE${NC}"
    fi
}

# Stop server
stop() {
    if ! is_running; then
        echo -e "${YELLOW}Server is not running${NC}"
        return 0
    fi

    echo -e "${BLUE}Stopping Smart Commands server...${NC}"

    local pid=$(cat "$PID_FILE")
    kill "$pid" 2>/dev/null

    # Wait for process to stop
    local count=0
    while ps -p "$pid" > /dev/null 2>&1 && [ $count -lt 10 ]; do
        sleep 1
        count=$((count + 1))
    done

    # Force kill if still running
    if ps -p "$pid" > /dev/null 2>&1; then
        kill -9 "$pid" 2>/dev/null
    fi

    rm -f "$PID_FILE"
    echo -e "${GREEN}✓ Server stopped${NC}"
}

# Restart server
restart() {
    echo -e "${BLUE}Restarting Smart Commands server...${NC}"
    stop
    sleep 2
    start
}

# Server status
status() {
    if is_running; then
        local pid=$(cat "$PID_FILE")
        echo -e "${GREEN}✓ Server is running (PID: $pid)${NC}"

        # Check health endpoint
        if curl -s "http://localhost:$SERVER_PORT/actuator/health" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Server is healthy${NC}"
        else
            echo -e "${YELLOW}⚠ Server is running but health check failed${NC}"
        fi

        return 0
    else
        echo -e "${RED}✗ Server is not running${NC}"
        return 1
    fi
}

# Show logs
logs() {
    if [ -f "$LOG_FILE" ]; then
        tail -f "$LOG_FILE"
    else
        echo -e "${YELLOW}No logs found${NC}"
    fi
}

# Main command dispatcher
case "${1:-}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    logs)
        logs
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs}"
        exit 1
        ;;
esac
EOF

    chmod +x "$SCRIPTS_DIR/smart-commands-server.sh"
    print_success "Server management script created"
}

# Step 8: Create Shell Integration Script
create_shell_integration() {
    print_step "Creating shell integration script..."

    # Copy the enhanced shell script from resources
    if [ -f "$PROJECT_DIR/src/main/resources/smart-commands-enhanced.sh" ]; then
        cp "$PROJECT_DIR/src/main/resources/smart-commands-enhanced.sh" "$SCRIPTS_DIR/smart-commands-shell.sh"

        # Update paths in the shell script
        sed -i.bak "s|SMART_COMMANDS_BIN=\"\$HOME/.local/bin/smart-commands\"|SMART_COMMANDS_BIN=\"$SCRIPTS_DIR/smart-commands-server.sh\"|g" "$SCRIPTS_DIR/smart-commands-shell.sh"
        rm -f "$SCRIPTS_DIR/smart-commands-shell.sh.bak"

        chmod +x "$SCRIPTS_DIR/smart-commands-shell.sh"
        print_success "Shell integration script created"
    else
        print_error "Shell integration template not found"
        exit 1
    fi
}

# Step 9: Integrate with .zshrc
integrate_zshrc() {
    print_step "Integrating with .zshrc..."

    local zshrc="$HOME/.zshrc"
    local integration_marker="# Smart Commands Integration"

    # Check if already integrated
    if grep -q "$integration_marker" "$zshrc" 2>/dev/null; then
        print_warning "Smart Commands is already integrated in .zshrc"

        # Ask user if they want to update
        read -p "Do you want to update the integration? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "Skipping .zshrc integration"
            return 0
        fi

        # Remove old integration
        sed -i.bak "/$integration_marker/,/# End Smart Commands Integration/d" "$zshrc"
    fi

    # Add integration
    cat >> "$zshrc" << EOF

# Smart Commands Integration
# Source the Smart Commands shell integration
if [ -f "$SCRIPTS_DIR/smart-commands-shell.sh" ]; then
    source "$SCRIPTS_DIR/smart-commands-shell.sh"
fi

# Optional: Auto-start server on shell startup (uncomment to enable)
# if [ -f "$SCRIPTS_DIR/smart-commands-server.sh" ]; then
#     "$SCRIPTS_DIR/smart-commands-server.sh" start > /dev/null 2>&1
# fi

# End Smart Commands Integration
EOF

    print_success ".zshrc integration complete"
    print_info "Shell integration will be active in new terminal sessions"
}

# Step 10: Start Server
start_server() {
    print_step "Starting Smart Commands server..."

    "$SCRIPTS_DIR/smart-commands-server.sh" start
}

# Step 11: Setup Auto-start Services
setup_autostart() {
    print_step "Setting up auto-start services..."

    local os=$(detect_os)

    if [ "$os" = "macos" ]; then
        setup_macos_autostart
    elif [ "$os" = "linux" ]; then
        setup_linux_autostart
    else
        print_warning "Auto-start setup not supported for this OS"
        return 0
    fi

    print_success "Auto-start services configured"
}

# Setup macOS auto-start using launchd
setup_macos_autostart() {
    print_step "Configuring macOS auto-start with launchd..."

    # Create LaunchAgents directory
    local launch_agents_dir="$HOME/Library/LaunchAgents"
    mkdir -p "$launch_agents_dir"

    # Create Ollama auto-start plist
    cat > "$launch_agents_dir/com.ollama.ollama.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.ollama.ollama</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/local/bin/ollama</string>
        <string>serve</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>$LOGS_DIR/ollama.log</string>
    <key>StandardErrorPath</key>
    <string>$LOGS_DIR/ollama-error.log</string>
</dict>
</plist>
EOF

    # Create Smart Commands auto-start plist
    cat > "$launch_agents_dir/com.smartcommands.server.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.smartcommands.server</string>
    <key>ProgramArguments</key>
    <array>
        <string>$SCRIPTS_DIR/smart-commands-server.sh</string>
        <string>start</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>StartInterval</key>
    <integer>60</integer>
    <key>StandardOutPath</key>
    <string>$LOGS_DIR/smart-commands-autostart.log</string>
    <key>StandardErrorPath</key>
    <string>$LOGS_DIR/smart-commands-autostart-error.log</string>
    <key>WorkingDirectory</key>
    <string>$INSTALL_DIR</string>
</dict>
</plist>
EOF

    # Load the launch agents
    launchctl load "$launch_agents_dir/com.ollama.ollama.plist" 2>/dev/null || print_warning "Could not load Ollama launch agent"
    launchctl load "$launch_agents_dir/com.smartcommands.server.plist" 2>/dev/null || print_warning "Could not load Smart Commands launch agent"

    print_success "macOS auto-start services configured"
}

# Setup Linux auto-start using systemd
setup_linux_autostart() {
    print_step "Configuring Linux auto-start with systemd..."

    # Create systemd user service directory
    local systemd_dir="$HOME/.config/systemd/user"
    mkdir -p "$systemd_dir"

    # Create Ollama systemd service
    cat > "$systemd_dir/ollama.service" << EOF
[Unit]
Description=Ollama AI Service
After=network-online.target

[Service]
Type=simple
ExecStart=/usr/local/bin/ollama serve
Restart=always
RestartSec=10
StandardOutput=file:$LOGS_DIR/ollama.log
StandardError=file:$LOGS_DIR/ollama-error.log

[Install]
WantedBy=default.target
EOF

    # Create Smart Commands systemd service
    cat > "$systemd_dir/smart-commands.service" << EOF
[Unit]
Description=Smart Commands Server
After=network-online.target ollama.service

[Service]
Type=forking
ExecStart=$SCRIPTS_DIR/smart-commands-server.sh start
ExecStop=$SCRIPTS_DIR/smart-commands-server.sh stop
Restart=always
RestartSec=10
StandardOutput=file:$LOGS_DIR/smart-commands-autostart.log
StandardError=file:$LOGS_DIR/smart-commands-autostart-error.log
WorkingDirectory=$INSTALL_DIR
PIDFile=$INSTALL_DIR/smart-commands.pid

[Install]
WantedBy=default.target
EOF

    # Reload systemd and enable services
    systemctl --user daemon-reload
    systemctl --user enable ollama.service 2>/dev/null || print_warning "Could not enable Ollama service"
    systemctl --user enable smart-commands.service 2>/dev/null || print_warning "Could not enable Smart Commands service"

    # Start the services
    systemctl --user start ollama.service 2>/dev/null || print_warning "Could not start Ollama service"
    systemctl --user start smart-commands.service 2>/dev/null || print_warning "Could not start Smart Commands service"

    print_success "Linux auto-start services configured"
}

# Step 12: Display Installation Summary
show_summary() {
    print_header "Installation Complete!"

    echo -e "${GREEN}Smart Commands v$APP_VERSION has been successfully installed!${NC}"
    echo
    echo -e "${CYAN}Installation Directory:${NC} $INSTALL_DIR"
    echo -e "${CYAN}Server Management:${NC} $SCRIPTS_DIR/smart-commands-server.sh"
    echo -e "${CYAN}Server Port:${NC} $SERVER_PORT"
    echo -e "${CYAN}Logs Directory:${NC} $LOGS_DIR"
    echo
    echo -e "${YELLOW}Server Management Commands:${NC}"
    echo -e "  Start server:   ${BLUE}$SCRIPTS_DIR/smart-commands-server.sh start${NC}"
    echo -e "  Stop server:    ${BLUE}$SCRIPTS_DIR/smart-commands-server.sh stop${NC}"
    echo -e "  Restart server: ${BLUE}$SCRIPTS_DIR/smart-commands-server.sh restart${NC}"
    echo -e "  Server status:  ${BLUE}$SCRIPTS_DIR/smart-commands-server.sh status${NC}"
    echo -e "  View logs:      ${BLUE}$SCRIPTS_DIR/smart-commands-server.sh logs${NC}"
    echo
    echo -e "${YELLOW}Auto-start Services:${NC}"
    echo -e "  ✓ Ollama service will start automatically on system boot"
    echo -e "  ✓ Smart Commands server will start automatically on system boot"
    echo -e "  ${BLUE}Logs: $LOGS_DIR/ollama.log${NC}"
    echo -e "  ${BLUE}Logs: $LOGS_DIR/smart-commands-autostart.log${NC}"
    echo
    echo -e "${YELLOW}Shell Integration:${NC}"
    echo -e "  The shell integration is now active in .zshrc"
    echo -e "  Reload your shell: ${BLUE}source ~/.zshrc${NC}"
    echo
    echo -e "${YELLOW}Usage Examples:${NC}"
    echo -e "  ${BLUE}sc 'find all java files'${NC}    - Generate command from description"
    echo -e "  ${BLUE}lsl -la${NC}                      - Auto-correct typos"
    echo -e "  ${BLUE}docker sp -a${NC}                 - Auto-correct subcommands"
    echo
    echo -e "${GREEN}Enjoy Smart Commands! 🚀${NC}"
    echo
}

################################################################################
# Main Installation Flow
################################################################################

main() {
    print_header "$APP_NAME Installer v$APP_VERSION"

    echo -e "${CYAN}This script will install Smart Commands on your system${NC}"
    echo -e "${CYAN}Installation directory: $INSTALL_DIR${NC}"
    echo

    # Run installation steps
    check_prerequisites
    create_directories
    install_java
    install_ollama
    download_ollama_model
    build_application
    create_server_script
    create_shell_integration
    integrate_zshrc
    setup_autostart
    start_server

    # Show summary
    show_summary
}

# Run main installation
main "$@"
