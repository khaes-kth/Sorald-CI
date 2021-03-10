package se.kth.castor.ci.githubapi.commits;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import se.kth.castor.ci.daemons.GithubScanner;
import se.kth.castor.ci.githubapi.commits.models.SelectedCommit;
import se.kth.castor.ci.githubapi.repositories.GithubAPIRepoAdapter;

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
    public void listCommits_withoutSpecifiedReposList() throws IOException {
        List<SelectedCommit> commits =
                GithubAPICommitAdapter.getInstance()
                        .getSelectedCommits(new Date().getTime() - 24L * 60 * 60 * 1000,
                                new Date().getTime(), 10000, GithubAPIRepoAdapter.MAX_STARS,
                                GithubScanner.FetchMode.ALL, false);

        Assert.assertFalse(commits.isEmpty());
    }

    @Test
    public void listCommits_withInvalidRepoList() throws IOException {
        List<SelectedCommit> commits =
                GithubAPICommitAdapter.getInstance()
                        .getSelectedCommits(new Date().getTime() - 24L * 60 * 60 * 1000,
                                new Date().getTime(), GithubScanner.FetchMode.ALL,
                                new HashSet<String>(List.of("unknown/not_defined")), false);

        Assert.assertTrue(commits.isEmpty());
    }

    @Test
    public void listCommits_withValidReposList() throws IOException {
        List<SelectedCommit> commits =
                GithubAPICommitAdapter.getInstance()
                        .getSelectedCommits(0,
                                new Date().getTime(), GithubScanner.FetchMode.ALL,
                                new HashSet<String>(List.of("khaes-kth/sorald-ci")), false);

        Assert.assertTrue(commits.size() > 10);
    }
}
