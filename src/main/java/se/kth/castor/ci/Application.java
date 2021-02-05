package se.kth.castor.ci;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.kth.castor.ci.daemons.GithubScanner;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application implements ApplicationRunner {
    @Value("${data.filepath}")
    private String dataFilePath;

    @Value("${rules}")
    private String rulesStr;

    private File dataFile;

    private GithubScanner githubScanner;

    public static void main(String[] args) throws IOException {
        SpringApplication.run(Application.class, args);
    }

    private void runDaemons() throws IOException {
        HashSet<String> repos =
                new HashSet<String>(FileUtils.readLines((new ClassPathResource("target_repos.txt").getFile()),
                "UTF-8"));
        githubScanner = new GithubScanner(GithubScanner.FetchMode.ALL, repos, dataFile,
                Arrays.asList(rulesStr.split(",")));
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

@RestController
class Controller {
    @Value("${data.filepath}")
    private String dataFilePath;

    @RequestMapping("/stats")
    public ResponseEntity<String> fixCommits() throws IOException {
        File dataFile = new File(dataFilePath);

        if(!dataFile.exists()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(FileUtils.readFileToString(dataFile, "UTF-8"));
    }
}

