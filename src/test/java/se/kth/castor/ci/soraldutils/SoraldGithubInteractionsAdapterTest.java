package se.kth.castor.ci.soraldutils;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

public class SoraldGithubInteractionsAdapterTest {

    @Test
    public void cloneRepo_invalidRepo(@TempDir File tmpdir) {
        Assert.assertThrows(InvalidRemoteException.class, () -> {
            SoraldGithubInteractionsAdapter.getInstance(tmpdir.getPath())
                    .cloneRepo("INVALID", "INVALID", "INVALID");
        });
    }

    @Test
    public void cloneRepo_validRepo(@TempDir File tmpdir) throws IOException, GitAPIException {
        File file = SoraldGithubInteractionsAdapter.getInstance(tmpdir.getPath())
                .cloneRepo("https://github.com/khaes-kth/sorald-ci",
                        "4e93fd38df1e39734db2c6f4784027a7fc796c95", "tmp");

        Assert.assertTrue(FileUtils.sizeOfDirectory(file) > 0);
    }
}
