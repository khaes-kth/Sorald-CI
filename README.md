# SoraldBot
SoraldBot is a software bot that automatically monitors specified Github repositories, detects commits that intorduce new violations of [SonarQube](https://www.sonarqube.org/) rules, fixes those violations, and generates a patch including those violations.

*This code is only developed for the purpose of evaluating effects of integration of [Sorald](https://github.com/SpoonLabs/sorald) to mainstream development platforms. The final version will be integrated into [Repairnator](https://github.com/eclipse/repairnator).* 
## Directories
The `src` folder contains the code.

We save results of our (RQ2 & RQ3 in the paper) experiment in the `files/experiments/round1` directory.
- target_repos.txt: the list of repositories monitored.
- log1.zip: log files.
- data.csv: List of monitored commits and generated patches for each of them (last column).
- PRs.txt: Submitted pull requests.

## List of Commits
This program also provides an option to get the list of commits in projects with the following features:
- Repository has at least `min` stars.
- Pushed after 'time' with the format of 'YYYY-mm-dd'.
- Java and Maven program (with a `*.pom` file at root).

For example, have a look at the following command:
```
mvn spring-boot:run "-Dspring-boot.run.arguments=--commit.fetch.start.time=2021-03-01 --run.mode=fetch-commits --repos.min.stars=10000 --tokens.path=../config.ini --output.filepath=../output.txt"
```

Since SoraldBot is implemented using `spring-boot`, you can run it using the command above.

In this example, `time` is set to `2021-03-01`, `min` (minimum number of stars) is set to 10000.

`--run.mode` parameter is required to state that we just want to get list of commits and do not intent to use other features of Sorald.

`--tokens.path` specifies the path of a file containing Github access-tokens. The content of such a file should be similar to the following (it can include any number of tokens):
```
ACCESS_TOKEN1
ACCESS_TOKEN2
.
.
.
```

`--output.filepath` is the path to the output file, where SoraldBot will write down list of commits. Each line of the output file will be like the following (containing the commitId and repoName):

`SelectedCommit{commitId='d0ee3ac486b096b76e33b83bc5e19f68c651ce2e', repoName='redisson/redisson'}`