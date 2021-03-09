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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    @Value("${data.filepath}")
    private String dataFilePath;

    @Value("${rules}")
    private String rulesStr;

    @Value("${tmpdir}")
    private String tmpdir;

    @Value("${repos.path}")
    private String reposLstPath;

    @Value("${patch.printing.mode}")
    private String patchPrintingMode;

    @Value("${commit.fetch.frequency}")
    private Long commitFetchFrequency;

    @Value("${commit.fetch.start.time}")
    private Long commitFetchStartTime;

    private File dataFile;

    private GithubScanner githubScanner;

    public static void main(String[] args) throws IOException {
        logger.info("APPLICATION STARTED AT: " + new Date());
        SpringApplication.run(Application.class, args);
    }

    private void runDaemons() throws IOException {
        HashSet<String> repos =
                new HashSet<String>(FileUtils.readLines(new File(reposLstPath),
                "UTF-8"));
        githubScanner = new GithubScanner(GithubScanner.FetchMode.ALL, repos, dataFile,
                Arrays.asList(rulesStr.split(",")), tmpdir, patchPrintingMode);
        githubScanner.setDaemon(true);
        githubScanner.start();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        dataFile = new File(dataFilePath);

        if(!dataFile.exists())
            dataFile.createNewFile();

        runDaemons();
    }
}
