package se.kth.castor.ci.soraldutils.githubapi;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import se.kth.castor.ci.daemons.GithubScanner;
import se.kth.castor.ci.githubapi.commits.GithubAPICommitAdapter;
import se.kth.castor.ci.githubapi.commits.models.SelectedCommit;

import java.io.IOException;
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
}
