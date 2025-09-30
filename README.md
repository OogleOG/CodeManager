# Code manager.Snippet Manager

A lightweight Java desktop application for managing code snippets. Built with Swing, this app allows developers to organize, search, and reuse code snippets efficiently.

## Features
- **Add, Edit, Delete Snippets**: Quickly manage your personal library of code snippets.
- **Search**: Find snippets by title, language, description, or tags.
- **manager.Snippet Details**: View metadata such as creation/last modified date.
- **Preview Pane**: Display snippets in a monospaced font for easy reading.
- **Persistence**: Snippets are automatically saved to `snippets.dat` on exit and reloaded on startup.
- **Import/Export**: Backup or share your snippet collection with others.

## Usage
1. Clone or download the repository.
2. Compile the Java file:
   ```bash
   javac manager.CodeSnippetManager.java
   ```
3. Run the application:
   ```bash
   java manager.CodeSnippetManager
   ```

## File Structure
- `manager.CodeSnippetManager.java` – Main application entry point and UI.
- `snippets.dat` – Local storage file for snippets (auto-created on exit).

## Requirements
- Java 8 or higher.

## Roadmap
- [ ] Syntax highlighting for supported languages.
- [ ] GitHub Gist integration.

## License
This project is licensed under the MIT License. Feel free to use, modify, and distribute it.
