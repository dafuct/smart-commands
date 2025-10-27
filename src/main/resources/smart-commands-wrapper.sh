#!/bin/bash

# Smart Commands Enhanced Wrapper Script
# Handles Java requirements and provides fallback options

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="smart-commands"
JAR_FILE="$SCRIPT_DIR/smart-commands.jar"
DATA_DIR="${SMART_COMMANDS_DATA_DIR:-$HOME/.local/share/smart-commands}"
JAVA_OPTS="-Xmx512m"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Java is available and meets requirements
check_java() {
    if command -v java &> /dev/null; then
        java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
        if [ "$java_version" -ge 21 ]; then
            return 0  # Java 21+ available
        else
            log_warning "Java $java_version found, but Java 21+ is recommended."
            return 1  # Java available but version too low
        fi
    else
        return 2  # Java not available
    fi
}

# Try to find Java 21+ in common locations
find_java21() {
    local java_paths=(
        "/usr/libexec/java_home -v 21.0"
        "/usr/libexec/java_home -v 21"
        "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java"
        "/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home/bin/java"
        "/opt/homebrew/Cellar/openjdk@21/*/bin/java"
        "/usr/lib/jvm/java-21-openjdk/bin/java"
        "$HOME/.sdkman/candidates/java/current/bin/java"
    )
    
    for java_path in "${java_paths[@]}"; do
        if [ -x "$java_path" ]; then
            local version=$("$java_path" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
            if [ "$version" -ge 21 ]; then
                echo "$java_path"
                return 0
            fi
        fi
    done
    
    return 1
}

# Suggest Java installation
suggest_java_installation() {
    log_error "Java 21+ is required but not found."
    echo
    echo "Please install Java 21+:"
    echo
    echo "🍎  macOS (recommended):"
    echo "  brew install openjdk@21"
    echo "  brew install openjdk@21"
    echo
    echo "🐧 Ubuntu/Debian:"
    echo "  sudo apt update"
    echo "  sudo apt install openjdk-21-jdk"
    echo
    echo "📦 Download manually:"
    echo "  https://adoptium.net/"
    echo
    echo "After installation, please restart your terminal and try again."
}

# Run the application with available Java
run_with_java() {
    local java_cmd="$1"
    shift
    
    log_info "Starting Smart Commands with Java: $java_cmd"
    
    # Set data directory
    if [ ! -d "$DATA_DIR" ]; then
        mkdir -p "$DATA_DIR"
    fi
    
    # Run the application
    exec "$java_cmd" $JAVA_OPTS -Ddata.dir="$DATA_DIR" -jar "$JAR_FILE" "$@"
}

# Try to download and use a portable Java runtime
download_portable_java() {
    log_info "Attempting to download portable Java runtime..."
    
    # Create temporary directory for Java
    local temp_java_dir="$DATA_DIR/java-runtime"
    mkdir -p "$temp_java_dir"
    
    # For now, we'll suggest manual installation
    # In a real implementation, you could download a portable Java runtime here
    log_warning "Portable Java download not implemented yet."
    log_info "Please install Java 21+ manually and try again."
    
    return 1
}

# Check if JAR file exists
check_jar_file() {
    if [ ! -f "$JAR_FILE" ]; then
        log_error "JAR file not found: $JAR_FILE"
        log_info "Please run the installation script first:"
        echo "  ./install.sh"
        exit 1
    fi
}

# Display help information
show_help() {
    cat << EOF
Smart Commands - Terminal Command Assistant

USAGE:
    $APP_NAME [OPTIONS] [COMMAND]

OPTIONS:
    --help, -h              Show this help message
    --version, -v           Show version information
    --status               Show application status
    --install-java         Show Java installation instructions

COMMANDS:
    sc 'task description'  Natural language to command conversion
    <command>              Process a command and get suggestions

EXAMPLES:
    $APP_NAME --help
    $APP_NAME sc 'find big files in current directory'
    $APP_NAME lss
    $APP_NAME --status

For more information, visit: https://github.com/your-repo/smart-commands
EOF
}

# Show version information
show_version() {
    if [ -f "$JAR_FILE" ]; then
        java -jar "$JAR_FILE" --version 2>/dev/null || echo "Smart Commands v1.0.0"
    else
        echo "Smart Commands v1.0.0 (JAR not found)"
    fi
}

# Show application status
show_status() {
    log_info "Checking Smart Commands status..."
    
    # Check JAR file
    if [ -f "$JAR_FILE" ]; then
        log_success "✓ JAR file found: $JAR_FILE"
    else
        log_error "✗ JAR file not found: $JAR_FILE"
        return 1
    fi
    
    # Check Java
    local java_status=$(check_java)
    case $java_status in
        0) log_success "✓ Java 21+ available" ;;
        1) log_warning "⚠ Java available but version < 21" ;;
        2) log_warning "⚠ Java not found" ;;
    esac
    
    # Check Ollama
    if command -v ollama &> /dev/null; then
        log_success "✓ Ollama installed"
        if ollama list &> /dev/null 2>/dev/null; then
            log_success "✓ Ollama model available"
        else
            log_warning "⚠ Ollama installed but model not loaded"
        fi
    else
        log_warning "⚠ Ollama not installed"
    fi
    
    # Check data directory
    if [ -d "$DATA_DIR" ]; then
        log_success "✓ Data directory: $DATA_DIR"
    else
        log_warning "⚠ Data directory not found: $DATA_DIR"
    fi
    
    echo
    log_info "Application is ready to run!"
}

# Main execution
main() {
    # Handle help and special flags
    case "${1:-}" in
        --help|-h)
            show_help
            exit 0
            ;;
        --version|-v)
            show_version
            exit 0
            ;;
        --status)
            show_status
            exit 0
            ;;
        --install-java)
            suggest_java_installation
            exit 0
            ;;
    esac
    
    # Check if JAR file exists
    check_jar_file
    
    # Check Java availability
    local java_status=$(check_java)
    
    case $java_status in
        0)
            # Java 21+ available, use it directly
            run_with_java java "$@"
            ;;
        1)
            # Java available but version too low, try to find Java 21
            log_info "Searching for Java 21+..."
            local java21_cmd=$(find_java21)
            if [ $? -eq 0 ]; then
                log_success "Found Java 21+: $java21_cmd"
                run_with_java "$java21_cmd" "$@"
            else
                log_error "Java 21+ not found on system."
                suggest_java_installation
                exit 1
            fi
            ;;
        2)
            # Java not available, try to find Java 21
            log_info "Java not found, searching for Java 21+..."
            local java21_cmd=$(find_java21)
            if [ $? -eq 0 ]; then
                log_success "Found Java 21+: $java21_cmd"
                run_with_java "$java21_cmd" "$@"
            else
                log_error "Java 21+ not found on system."
                suggest_java_installation
                exit 1
            fi
            ;;
    esac
}

# Run main function with all arguments
main "$@"
