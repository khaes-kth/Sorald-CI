package se.kth.castor.ci.daemons;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.castor.ci.githubapi.commits.GithubAPICommitAdapter;
import se.kth.castor.ci.githubapi.commits.models.SelectedCommit;
import se.kth.castor.ci.githubapi.repositories.GithubAPIRepoAdapter;
import se.kth.castor.ci.soraldutils.SoraldCIAdapter;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class GithubScanner extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(GithubScanner.class);
    private static final long DEFAULT_FREQUENCY = 1 * 60 * 60 * 1000L;

    private FetchMode fetchMode;
    private Set<String> repos;
    private long lastFetched = -1L;
    private File dataFile;
    private List<String> rules;
    private String tmpdir;
    private String patchPrintingMode;
    private long frequency;
    private long startTime;
    private Integer minStars;

    public GithubScanner
            (
                    FetchMode fetchMode,
                    Set<String> repos,
                    File dataFile,
                    List<String> rules,
                    String tmpdir,
                    String patchPrintingMode,
                    Long frequency,
                    Long startTime,
                    Integer minStars
            ) {
        this.fetchMode = fetchMode;
        this.repos = repos;
        this.dataFile = dataFile;
        this.rules = rules;
        this.tmpdir = tmpdir;
        this.patchPrintingMode = patchPrintingMode;
        this.lastFetched = new Date().getTime();
        this.frequency = frequency == null ? DEFAULT_FREQUENCY : frequency;
        this.startTime = startTime == null ? new Date().getTime() : startTime;
        this.minStars = minStars;
    }

    @Override
    public void run() {
        while (true) {
            long now = startTime;

            try {
                List<SelectedCommit> selectedCommits = fetch(
                        lastFetched, now
                );

                for (SelectedCommit commit : selectedCommits)
                    try {
                        process(commit);
                    } catch (Exception e) {
                        logger.error("error while repairing: " + commit.getCommitUrl());
                        e.printStackTrace();
                    }

            } catch (Exception e) {
                logger.error("error while processing: " + new Date(lastFetched) + " to " + new Date(now));
                e.printStackTrace();
            }

            lastFetched = now;

            try {
                TimeUnit.MILLISECONDS.sleep(frequency);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private List<SelectedCommit> fetch(long startTime, long endTime) throws Exception {
        return minStars == null ?
                GithubAPICommitAdapter.getInstance().getSelectedCommits
                        (startTime, endTime, fetchMode, repos, false) :
                GithubAPICommitAdapter.getInstance().getSelectedCommits
                        (startTime, endTime, minStars, GithubAPIRepoAdapter.MAX_STARS, fetchMode, false);
    }

    private void process(SelectedCommit commit) throws IOException, GitAPIException, ParseException, InterruptedException {
        List<String> fixedCommitUrl = SoraldCIAdapter.getInstance(tmpdir, patchPrintingMode).repairAndCreateForks(commit, rules);

        logger.info("repaired: " + commit.getCommitUrl());
        FileUtils.writeStringToFile(dataFile, new Date() + "," + commit.getCommitUrl() + ","
                        + fixedCommitUrl + System.lineSeparator(), "UTF-8", true);
    }

    public enum FetchMode {
        FAILED, ALL;
    }
}
