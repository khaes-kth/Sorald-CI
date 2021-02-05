package se.kth.castor.ci.githubapi.commits.models;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

public class SelectedCommit {
    private static final String COMMIT_URL_TEMPLATE = "https://github.com/{repo}/commit/{commit}";
    private static final String REPO_URL_TEMPLATE = "https://github.com/{repo}.git";
    private Boolean isGithubActionsFailed;
    private String commitId;
    private String repoName;
    private String commitUrl;

    public SelectedCommit
            (
                    Boolean isGithubActionsFailed,
                    String commitId,
                    String repoName
            ) {
        this.isGithubActionsFailed = isGithubActionsFailed;
        this.commitId = commitId;
        this.repoName = repoName;
        this.commitUrl = COMMIT_URL_TEMPLATE.replace("{repo}", repoName).replace("{commit}", commitId);
    }

    public String getCommitUrl() {
        return commitUrl;
    }

    public void setCommitUrl(String commitUrl) {
        this.commitUrl = commitUrl;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public Boolean getGithubActionsFailed() {
        return isGithubActionsFailed;
    }

    public void setGithubActionsFailed(Boolean githubActionsFailed) {
        isGithubActionsFailed = githubActionsFailed;
    }

    public String getRepoUrl(){
        return REPO_URL_TEMPLATE.replace("{repo}", repoName);
    }

    @Override
    public String toString() {
        return "SelectedCommit{" +
                "commitId='" + commitId + '\'' +
                ", repoName='" + repoName + '\'' +
                '}';
    }
}
