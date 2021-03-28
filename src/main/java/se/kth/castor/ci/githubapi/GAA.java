package se.kth.castor.ci.githubapi;

import org.apache.commons.io.FileUtils;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

// GAM stands for: Github Api Adapter
public class GAA {
    private static final Logger LOGGER = LoggerFactory.getLogger(GAA.class);

    public static final String TOKENS_PATH_PROPERTY_KEY = "tokens.path";
    public static final String USER_HOME_PROPERTY_KEY = "user.home";
    public static final String TOKENS_RELATIVE_FILE_PATH = "/Downloads/config.ini";
    
    private static final String TOKENS_PATH =
            System.getProperty(TOKENS_PATH_PROPERTY_KEY) != null ?
            System.getProperty(TOKENS_PATH_PROPERTY_KEY) :
            System.getProperty(USER_HOME_PROPERTY_KEY) + TOKENS_RELATIVE_FILE_PATH;
    private static int lastUsedToken = 0;

    public static GitHub g() throws IOException {
        GitHubBuilder githubBuilder = new GitHubBuilder();

        if (tokens != null && tokens.length > 0)
            githubBuilder = githubBuilder.withOAuthToken(tokens[lastUsedToken++ % tokens.length]);

        return githubBuilder.build();
    }

    static {
        try {
            tokens = FileUtils.readLines(new File(TOKENS_PATH),
                    "UTF-8").toArray(new String[0]);
        } catch (IOException e) {
            LOGGER.info("No Github-API token file is provided. Repairnator will work without tokens.");
        }
    }

    private static String[] tokens;
}
