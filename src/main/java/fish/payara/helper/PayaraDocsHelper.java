/*
 *
 * Copyright (c) 2024 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.helper;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import static java.time.Duration.ofSeconds;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PayaraDocsHelper {

    private static final Logger logger = LoggerFactory.getLogger(PayaraDocsHelper.class);

    static OpenAiChatModel model;

    public static void main(String[] args) {
        String apiKey = System.getProperty("payara.docs.openai.secret.key");
        boolean updateDocs = Boolean.getBoolean("payara.docs.update");
        String gptModel = "gpt-4o-mini";
        Double temperature = 0.7d;
        int apiTimeout = 180;
        model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(gptModel)
                .temperature(temperature)
                .timeout(ofSeconds(apiTimeout))
                .logRequests(true)
                .logResponses(true)
                .build();
        String repoUrl = "https://github.com/payara/payara.git";
        String localPath = "D:/payara"; // Path to clone the Payara repository
        String docsRepoUrl = "https://github.com/payara/Payara-Documentation.git";
        String docsLocalPath = "D:/Payara-Documentation"; // Path to clone the Payara Documentation repository

        try {
            // Step 1: Clone or check if Payara repository exists locally
            File repoDir = new File(localPath);
            if (repoDir.exists() && new File(repoDir, ".git").exists()) {
                logger.info("Payara repository already exists locally. Skipping clone...");
            } else {
                // Clone the Payara repository
                logger.info("Cloning Payara repository...");
                Git.cloneRepository().setURI(repoUrl).setDirectory(repoDir).call();
                logger.info("Payara repository cloned successfully!");
            }

            // Step 2: Search for specific Java files with annotations
            logger.info("Searching for Java files with specific annotations...");
            Map<String, String> serviceToFileMap = searchJavaFiles(repoDir);

            // Step 3: Clone or check if Payara Documentation repository exists locally
            File docsRepoDir = new File(docsLocalPath);
            if (docsRepoDir.exists() && new File(docsRepoDir, ".git").exists()) {
                logger.info("Payara Documentation repository already exists locally. Skipping clone...");
            } else {
                // Clone the Payara Documentation repository
                logger.info("Cloning Payara Documentation repository...");
                Git.cloneRepository().setURI(docsRepoUrl).setDirectory(docsRepoDir).call();
                logger.info("Payara Documentation repository cloned successfully!");
            }

            // Step 4: Check for corresponding .adoc files in the Payara Documentation
            if (updateDocs) {
                updateAdocFiles(docsRepoDir, serviceToFileMap);
            } else {
                checkForAdocFiles(docsRepoDir, serviceToFileMap);
            }

        } catch (GitAPIException | IOException e) {
            logger.error("Error: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> searchJavaFiles(File directory) throws IOException {
        Map<String, String> serviceToFileMap = new HashMap<>();
        if (!directory.isDirectory()) {
            return serviceToFileMap;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return serviceToFileMap;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                serviceToFileMap.putAll(searchJavaFiles(file)); // Recursively search directories
            } else if (file.getName().endsWith(".java")) {
                checkFileForAnnotations(file, serviceToFileMap);
            }
        }
        return serviceToFileMap;
    }

    private static void checkFileForAnnotations(File file, Map<String, String> serviceToFileMap) throws IOException {
        boolean foundImport = false;
        String serviceName = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Check for the specific import
                if (line.contains("import org.jvnet.hk2.annotations.Service;")) {
                    foundImport = true;
                }

                // Check for @Service annotation
                if (foundImport) {
                    Pattern annotationPattern = Pattern.compile("@Service\\(name\\s*=\\s*\"(.*?)\"\\)");
                    Matcher matcher = annotationPattern.matcher(line);
                    if (matcher.find()) {
                        serviceName = matcher.group(1);
                        // Store the mapping of serviceName to the file path
                        serviceToFileMap.put(serviceName, file.getAbsolutePath());
                        break;
                    }
                }
            }
        }
    }

    private static void checkForAdocFiles(File docsRepoDir, Map<String, String> serviceToFileMap) throws IOException {
        File docsDirectory = new File(docsRepoDir, "docs/modules/ROOT/pages/Technical Documentation/Payara Server Documentation/Command Reference");
        if (!docsDirectory.exists()) {
            logger.info("Docs directory not found.");
            return;
        }

        File[] adocFiles = docsDirectory.listFiles((dir, name) -> name.endsWith(".adoc"));
        if (adocFiles == null || adocFiles.length == 0) {
            logger.info("No .adoc files found.");
            return;
        }

        int i = 0, o = 0;
        // Iterate through adocFiles first
        for (File adocFile : adocFiles) {
            i++;
            // Check if serviceName exists in the map
            String adocFileName = adocFile.getName().replace(".adoc", "");
            if (serviceToFileMap.containsKey(adocFileName)) {
                // If serviceName is found in the map, print the corresponding Java file
                String javaFilePath = serviceToFileMap.get(adocFileName);
                String javaFileContent = new String(Files.readAllBytes(Paths.get(javaFilePath)));
                String adocFileContent = new String(Files.readAllBytes(Paths.get(adocFile.getAbsolutePath())));
                String prompt = String.format(
                        """
                You are an expert at software documentation consistency. Compare the following AsciiDoc and Java file content:
                
                --- AsciiDoc Content ---
                %s
                
                --- Java File Content ---
                %s
                
                Analyze the following:
                    1. List any discrepancies in parameters (including naming conventions and missing parameters).
                    2. Highlight new features or functionality in the Java file that is not documented in the AsciiDoc.
                    3. Suggest updates to the AsciiDoc for missing or inconsistent details, without mentioning error handling, action report, repeating points or examples.
                    4. Highlight any discrepancies between the documentation and the Java implementation.
                                
                Provide a concise and short comparison to the point.
                """,
                        adocFileContent,
                        javaFileContent
                );
                String response = model.generate(prompt);
                i = i + prompt.length();
                o = o + response.length();
                // apend response in gpt.adoc with content as response with header ## adocFileName
                // creatae gpt.adoc if not exist
                Files.write(Paths.get("gpt.adoc"), ("## " + adocFileName + "\n" + response + "\n\n ========================================= \n\n").getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                // If serviceName is not found in the map, print a message
                logger.info("No matching Java file found for .adoc file: " + adocFile.getAbsolutePath());
            }
        }
        logger.info("Total input i " + i);
        logger.info("Total output o " + o);
    }

    private static void updateAdocFiles(File docsRepoDir, Map<String, String> serviceToFileMap) throws IOException {
        File docsDirectory = new File(docsRepoDir, "docs/modules/ROOT/pages/Technical Documentation/Payara Server Documentation/Command Reference");
        if (!docsDirectory.exists()) {
            logger.info("Docs directory not found.");
            return;
        }

        File[] adocFiles = docsDirectory.listFiles((dir, name) -> name.endsWith(".adoc"));
        if (adocFiles == null || adocFiles.length == 0) {
            logger.info("No .adoc files found.");
            return;
        }

        int i = 0, o = 0;
        for (File adocFile : adocFiles) {
            i++;
            String adocFileName = adocFile.getName().replace(".adoc", "");

            if (serviceToFileMap.containsKey(adocFileName)) {
                String javaFilePath = serviceToFileMap.get(adocFileName);
                String javaFileContent = new String(Files.readAllBytes(Paths.get(javaFilePath)));
                String adocFileContent = new String(Files.readAllBytes(Paths.get(adocFile.getAbsolutePath())));

                String prompt = String.format(
                        """
                    You are an expert at software documentation consistency. Update the AsciiDoc content to include the latest details from the Java implementation:
                    
                    --- Existing AsciiDoc Content ---
                    %s
                    
                    --- Java File Content ---
                    %s
                    
                    Update the documentation with:
                        1. Missing or new features from the Java implementation.
                        2. Correct parameter names and include missing ones, if any.
                        3. Fix any inconsistencies without adding unnecessary information.
                        
                    Provide the revised AsciiDoc content as a response.
                    """,
                        adocFileContent,
                        javaFileContent
                );

                String updatedAdocContent = model.generate(prompt);
                i = i + prompt.length();
                o = o + updatedAdocContent.length();

                // Write the updated content back to the .adoc file
                Files.write(Paths.get(adocFile.getAbsolutePath()), updatedAdocContent.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);

            } else {
                logger.info("No matching Java file found for .adoc file: " + adocFile.getAbsolutePath());
            }
        }
        logger.info("Total input i " + i);
        logger.info("Total output o " + o);
    }

}
