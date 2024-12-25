# Maven Adoc File Updater

This project automates the process of updating AsciiDoc (`.adoc`) files in Maven-based projects. It integrates with OpenAI's API to generate documentation suggestions, analyze existing files, and optionally perform direct updates.

## Installation

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd <repository-folder>
   ```
2. Build the project:
   ```bash
   mvn clean install
   ```
## Configuration

This project requires specific system properties to be set for seamless integration and functionality. Below are the properties and their purposes:

### System Properties

1. **`payara.docs.openai.secret.key`**  
   - **Description**: Specifies the API key for accessing OpenAI's services.  

2. **`payara.docs.update`**  
   - **Description**: A boolean flag used to determine whether documentation updates should be performed directly or generate a `gpt.adoc` file containing suggestions, recommendations, and analyses without altering existing files.

### How to Set System Properties

You can set these properties using the `-D` flag when running the application:

```bash
java -Dpayara.docs.openai.secret.key=your-api-key \
     -Dpayara.docs.update=true \
     -jar PayaraDocsHelper-1.0-SNAPSHOT.jar