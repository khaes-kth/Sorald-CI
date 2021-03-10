package se.kth.castor.ci;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import se.kth.castor.ci.daemons.GithubScanner;
import se.kth.castor.ci.githubapi.commits.GithubAPICommitAdapter;
import se.kth.castor.ci.githubapi.commits.models.SelectedCommit;
import se.kth.castor.ci.githubapi.repositories.GithubAPIRepoAdapter;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application implements ApplicationRunner {
    private static final String FETCH_COMMITS_MODE = "fetch-commits";
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    @Value("${data.filepath:#{null}}")
    private String dataFilePath;

    @Value("${rules:#{null}}")
    private String rulesStr;

    @Value("${tmpdir:#{null}}")
    private String tmpdir;

    @Value("${repos.path:#{null}}")
    private String reposLstPath;

    @Value("${patch.printing.mode:#{null}}")
    private String patchPrintingMode;

    @Value("${commit.fetch.frequency:#{null}}")
    private Long commitFetchFrequency;

    @Value("${commit.fetch.start.time:#{null}}")
    private String commitFetchStartTime;

    @Value("${repos.min.stars:#{null}}")
    private Integer reposMinStars;

    @Value("${tokens.path:#{null}}")
    private String tokensPath;

    @Value("${run.mode:#{null}}")
    private String runMode;

    private File dataFile;

    private GithubScanner githubScanner;

    public static void main(String[] args) {
        logger.info("APPLICATION STARTED AT: " + new Date());
        SpringApplication.run(Application.class, args);
    }

    private void runDaemons() throws IOException {
        HashSet<String> repos =
                new HashSet<String>(FileUtils.readLines(new File(reposLstPath),
                        "UTF-8"));

        Long startTime = null;
        if(commitFetchStartTime != null){
            startTime = getFetchStartTimeAsLong();
        }

        githubScanner = new GithubScanner(GithubScanner.FetchMode.ALL, repos, dataFile,
                Arrays.asList(rulesStr.split(",")), tmpdir, patchPrintingMode,
                commitFetchFrequency, startTime, reposMinStars);
        githubScanner.setDaemon(true);
        githubScanner.start();
    }

    private Long getFetchStartTimeAsLong() {
        Long startTime;
        String[] parts = commitFetchStartTime.split("-");
        Calendar cal = Calendar.getInstance();
        cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
        startTime = cal.getTimeInMillis();
        return startTime;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (tokensPath != null) {
            System.setProperty("tokens.path", tokensPath);
        }

        if (runMode.equals(FETCH_COMMITS_MODE)) {
            List<SelectedCommit> commits =
                    GithubAPICommitAdapter.getInstance().getSelectedCommits
                            (
                                    getFetchStartTimeAsLong(),
                                    new Date().getTime(), reposMinStars,
                                    GithubAPIRepoAdapter.MAX_STARS,
                                    GithubScanner.FetchMode.ALL,
                                    false
                            );

            commits.stream().forEach(System.out::println);
        } else {
            dataFile = new File(dataFilePath);

            if (!dataFile.exists())
                dataFile.createNewFile();

            runDaemons();
        }
    }
}
