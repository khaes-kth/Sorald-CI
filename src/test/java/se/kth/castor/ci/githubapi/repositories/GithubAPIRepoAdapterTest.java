package se.kth.castor.ci.githubapi.repositories;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.Set;

public class GithubAPIRepoAdapterTest {

    @Test
    public void listJavaRepositories_withStars() throws IOException {
        Calendar cal = Calendar.getInstance();
        cal.set(2021, 1, 1);

        long intervalStart = cal.getTime().getTime();

        Set<String> repos = GithubAPIRepoAdapter.getInstance()
                .listJavaRepositories(intervalStart, 1000, GithubAPIRepoAdapter.MAX_STARS);

        Assert.assertTrue(repos.size() > 5);
    }
}
