package se.kth.castor.ci.githubapi;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GitHub;
import se.kth.castor.ci.githubapi.GAA;

import java.io.IOException;

public class GAATest {

    @Test
    public void getGithub() throws IOException {
        GitHub github = GAA.g();

        Assert.assertNotNull(github);
    }
}
