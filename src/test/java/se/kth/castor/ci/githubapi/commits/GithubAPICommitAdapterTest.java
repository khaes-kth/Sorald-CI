package se.kth.castor.ci.githubapi.commits;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import se.kth.castor.ci.daemons.GithubScanner;
import se.kth.castor.ci.githubapi.commits.models.SelectedCommit;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

public class GithubAPICommitAdapterTest {

    @Test
    public void listCommits_withEmptyRepoList() throws IOException {
        List<SelectedCommit> commits = GithubAPICommitAdapter.getInstance()
                .getSelectedCommits(0, new Date().getTime(), GithubScanner.FetchMode.ALL, new HashSet<String>(),
                        false);

        Assert.assertTrue(commits.isEmpty());
    }

    @Test
    public void listCommits_withNullRepoList() throws IOException {
        List<SelectedCommit> commits =
                GithubAPICommitAdapter.getInstance()
                        .getSelectedCommits(new Date().getTime() - 2L * 60 * 60 * 1000,
                                new Date().getTime(),
                                GithubScanner.FetchMode.ALL, null, false);

        Assert.assertFalse(commits.isEmpty());
    }
}
