package se.kth.castor.ci.githubapi.repositories;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.Set;

public class GithubAPIRepoAdapterTest {
    static long firstDayOf2021;

    @BeforeAll
    public static void setUp(){
        Calendar cal = Calendar.getInstance();
        cal.set(2021, 1, 1);

        firstDayOf2021 = cal.getTime().getTime();
    }

    @Test
    public void listJavaRepositories_withStars() throws IOException {
        Set<String> repos = GithubAPIRepoAdapter.getInstance()
                .listJavaRepositories(firstDayOf2021, 1000, GithubAPIRepoAdapter.MAX_STARS);

        Assert.assertTrue(repos.size() > 5);
    }

    @Test
    public void listJavaRepositories_withInvalidStarLimits() throws IOException {
        Set<String> repos = GithubAPIRepoAdapter.getInstance()
                .listJavaRepositories(firstDayOf2021, 1000, -1);

        Assert.assertTrue(repos.isEmpty());
    }
}
