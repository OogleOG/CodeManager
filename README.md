# CodeSnippet Manager

A lightweight and modern JavaFX desktop application for managing code snippets.  
This app helps developers organize, search, and reuse their snippets with a clean and professional UI.

## ✨ Features
- **Add, Edit, Delete Snippets** – Quickly manage your personal library of code snippets.  
- **Search & Filter** – Find snippets by title, language, description, or tags.  
- **Function Extraction** – Automatically detect and store functions from source files.  
- **Snippet Details** – View metadata such as creation and last modified date.  
- **Preview Pane** – Display snippets in a monospaced font for easy reading.  
- **Persistence** – Snippets are automatically saved locally on exit and reloaded on startup.  
- **Import/Export** – Backup or share your snippet collection with others.  
- **GitHub Gist Integration** – Instantly create gists from your snippets, with the URL copied to your clipboard for easy sharing.  

## 🚀 Usage
1. Clone or download the repository.  
2. Build with Gradle or Maven (or compile manually):  
   ```bash
   javac -d out src/manager/*.java
   ```  
3. Run the application:  
   ```bash
   java -cp out manager.CodeSnippetManagerFX
   ```  

## 📂 File Structure
- `manager/CodeSnippetManagerFX.java` – Main application entry point (JavaFX UI).  
- `manager/CodeParser.java` – Handles function and snippet extraction.  
- `manager/GistService.java` – GitHub Gist integration.  
- `snippets.dat` – Local storage file for snippets (auto-created on exit).  

## 🛠 Requirements
- **Java 11+** (JavaFX requires at least Java 11; Java 20 recommended).  
- Internet connection (for Gist creation).  

## 📌 Roadmap
- [X] Syntax highlighting for supported languages.  
- [X] Categorization and tagging system improvements.  
- [X] Cloud sync support (Google Drive / Dropbox).  

## 📜 License
This project is licensed under the **MIT License**.  
Feel free to use, modify, and distribute it.  
