package se.kth.castor.ci.githubapi.commits;

import org.kohsuke.github.*;
import se.kth.castor.ci.daemons.GithubScanner;
import se.kth.castor.ci.githubapi.GAA;
import se.kth.castor.ci.githubapi.commits.models.SelectedCommit;
import se.kth.castor.ci.githubapi.repositories.GithubAPIRepoAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class GithubAPICommitAdapter {
    private static GithubAPICommitAdapter _instance;

    public static GithubAPICommitAdapter getInstance() {
        if (_instance == null)
            _instance = new GithubAPICommitAdapter();
        return _instance;
    }

    private List<SelectedCommit> getSelectedCommits
            (
                    GHRepository repo,
                    long since,
                    long until,
                    GithubScanner.FetchMode fetchMode,
                    boolean checkGithubActionsFailures
            ) throws IOException {
        List<SelectedCommit> res = new ArrayList<>();

        GHCommitQueryBuilder query = repo.queryCommits().since(since).until(until);
        for (GHCommit commit : query.list().toList()) {
            boolean isGithubActionsFailed = false;
            if(checkGithubActionsFailures) {
                for (GHCheckRun check : commit.getCheckRuns()) {
                    if (check.getApp().getName().equals("GitHub Actions") && !isGithubActionsFailed) {
                        if (check.getConclusion() != null && (!check.getConclusion().equals("success")
                                && !check.getConclusion().equals("neutral") && !check.getConclusion().equals("skipped"))) {
                            isGithubActionsFailed = true;
                        }
                    }
                    if (isGithubActionsFailed)
                        break;
                }
            }

            switch(fetchMode) {
                case ALL:
                    res.add(new SelectedCommit(isGithubActionsFailed, commit.getSHA1(), repo.getFullName()));
                    break;
                case FAILED:
                default:
                    if(isGithubActionsFailed)
                        res.add(new SelectedCommit(isGithubActionsFailed, commit.getSHA1(), repo.getFullName()));
                    break;
            }
        }

        return res;
    }

    public List<SelectedCommit> getSelectedCommits
            (
                    long intervalStart,
                    long intervalEnd,
                    GithubScanner.FetchMode fetchMode,
                    Set<String> repos,
                    boolean checkGithubActionsFailures
            ) throws IOException {
         repos = repos == null ? GithubAPIRepoAdapter.getInstance()
                .listJavaRepositories(intervalStart, 0, GithubAPIRepoAdapter.MAX_STARS) : repos;

        int cnt = 0;
        List<SelectedCommit> selectedCommits = new ArrayList<>();
        for (String repoName : repos) {
            try {
                GHRepository repo = GAA.g().getRepository(repoName);
                System.out.println("Checking commits for: " + repo.getName() + " " + cnt++ + " " + repos.size()
                        + " " + new Date(intervalStart));
                boolean isMaven = false;
                for (GHTreeEntry treeEntry : repo.getTree("HEAD").getTree()) {
                    if (treeEntry.getPath().equals("pom.xml")) {
                        isMaven = true;
                        break;
                    }
                }

                if (!isMaven) {
                    continue;
                }

                selectedCommits.addAll(GithubAPICommitAdapter.getInstance()
                        .getSelectedCommits(repo, intervalStart, intervalEnd, fetchMode, checkGithubActionsFailures));

            } catch (Exception e) {
                System.err.println("error occurred for: " + repoName);
                e.printStackTrace();
            }
        }
        return selectedCommits;
    }
}
