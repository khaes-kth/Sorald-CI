package se.kth.castor.ci.githubapi.commits.models;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class SelectedCommitTest {

    @Test
    public void checkToString(){
        SelectedCommit commit = new SelectedCommit(false, "07463d895e6dc7ef40573c9baf49bb157c61b7c3",
                "khaes-kth/Sorald-CI");

        Assert.assertEquals(commit.toString(),
                "SelectedCommit{commitId='07463d895e6dc7ef40573c9baf49bb157c61b7c3', repoName='khaes-kth/Sorald-CI'}");
    }
}
