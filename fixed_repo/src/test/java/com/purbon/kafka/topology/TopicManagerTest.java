package com.purbon.kafka.topology;

import static com.purbon.kafka.topology.BuilderCLI.ALLOW_DELETE_OPTION;
import static com.purbon.kafka.topology.BuilderCLI.BROKERS_OPTION;
import static com.purbon.kafka.topology.TopicManager.NUM_PARTITIONS;
import static com.purbon.kafka.topology.TopologyBuilderConfig.ALLOW_DELETE_TOPICS;
import static com.purbon.kafka.topology.TopologyBuilderConfig.KAFKA_INTERNAL_TOPIC_PREFIXES;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import com.purbon.kafka.topology.actions.Action;
import com.purbon.kafka.topology.api.adminclient.TopologyBuilderAdminClient;
import com.purbon.kafka.topology.model.Impl.ProjectImpl;
import com.purbon.kafka.topology.model.Impl.TopicImpl;
import com.purbon.kafka.topology.model.Impl.TopologyImpl;
import com.purbon.kafka.topology.model.Project;
import com.purbon.kafka.topology.model.Topic;
import com.purbon.kafka.topology.model.Topology;
import com.purbon.kafka.topology.schemas.SchemaRegistryManager;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class TopicManagerTest {

  @Mock TopologyBuilderAdminClient adminClient;

  @Mock SchemaRegistryManager schemaRegistryManager;

  @Mock BackendController backendController;
  ExecutionPlan plan;
  @Mock PrintStream outputStream;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private TopicManager topicManager;
  private HashMap<String, String> cliOps;

  @Before
  public void setup() throws IOException {
    cliOps = new HashMap<>();
    cliOps.put(BROKERS_OPTION, "");
    plan = ExecutionPlan.init(backendController, System.out);
    topicManager = new TopicManager(adminClient, schemaRegistryManager);
  }

  @Test
  public void newTopicCreationTest() throws IOException {

    Project project = new ProjectImpl("project");
    Topic topicA = new TopicImpl("topicA");
    project.addTopic(topicA);
    Topic topicB = new TopicImpl("topicB");
    project.addTopic(topicB);
    Topology topology = new TopologyImpl();
    topology.addProject(project);

    when(adminClient.listApplicationTopics()).thenReturn(new HashSet<>());
    topicManager.apply(topology, plan);
    plan.run();

    verify(adminClient, times(1)).createTopic(topicA, topicA.toString());
    verify(adminClient, times(1)).createTopic(topicB, topicB.toString());
  }

  @Test
  public void topicUpdateTest() throws IOException {

    Project project = new ProjectImpl("project");
    Topic topicA = new TopicImpl("topicA");
    project.addTopic(topicA);
    Topic topicB = new TopicImpl("topicB");
    topicB.getConfig().put(NUM_PARTITIONS, "12");
    project.addTopic(topicB);
    Topology topology = new TopologyImpl();
    topology.addProject(project);

    Set<String> dummyTopicList = new HashSet<>();
    dummyTopicList.add(topicB.toString());
    when(adminClient.listApplicationTopics()).thenReturn(dummyTopicList);

    topicManager.apply(topology, plan);
    plan.run();

    verify(adminClient, times(1)).createTopic(topicA, topicA.toString());
    verify(adminClient, times(1)).updateTopicConfig(topicB, topicB.toString());
    verify(adminClient, times(1)).getPartitionCount(topicB.toString());
    verify(adminClient, times(1)).updatePartitionCount(topicB, topicB.toString());
  }

  @Test
  public void topicDeleteTest() throws IOException {

    cliOps = new HashMap<>();
    cliOps.put(BROKERS_OPTION, "");
    cliOps.put(ALLOW_DELETE_OPTION, "true");

    Properties props = new Properties();

    TopologyBuilderConfig config = new TopologyBuilderConfig(cliOps, props);

    plan = ExecutionPlan.init(backendController, System.out);
    topicManager = new TopicManager(adminClient, schemaRegistryManager, config);

    // Original Topology
    Topology topology0 = new TopologyImpl();
    Project project0 = new ProjectImpl("project");
    topology0.addProject(project0);

    Topic topicC = new TopicImpl("topicC");
    project0.addTopic(topicC);

    // Topology after delete action
    Topology topology = new TopologyImpl();
    Project project = new ProjectImpl("project");
    topology.addProject(project);

    Topic topicA = new TopicImpl("topicA");
    project.addTopic(topicA);
    Topic topicB = new TopicImpl("topicB");
    project.addTopic(topicB);

    Set<String> dummyTopicList = new HashSet<>();
    String topicCFullName = topicC.toString();
    dummyTopicList.add(topicCFullName);
    dummyTopicList.add(
        "_my-internal-topic"); // return an internal topic using the default config values
    when(adminClient.listApplicationTopics()).thenReturn(dummyTopicList);

    topicManager.apply(topology, plan);
    plan.run();

    verify(adminClient, times(1)).createTopic(topicA, topicA.toString());
    verify(adminClient, times(1)).createTopic(topicB, topicB.toString());
    verify(adminClient, times(1)).deleteTopics(Collections.singletonList(topicCFullName));
  }

  @Test
  public void topicDeleteWithConfiguredInternalTopicsTest() throws IOException {

    cliOps = new HashMap<>();
    cliOps.put(BROKERS_OPTION, "");
    cliOps.put(ALLOW_DELETE_OPTION, "true");

    Properties props = new Properties();
    props.put(KAFKA_INTERNAL_TOPIC_PREFIXES, Arrays.asList("foo.", "_"));

    TopologyBuilderConfig config = new TopologyBuilderConfig(cliOps, props);

    TopicManager topicManager = new TopicManager(adminClient, schemaRegistryManager, config);

    // Topology after delete action
    Topology topology = new TopologyImpl();
    Project project = new ProjectImpl("project");
    topology.addProject(project);

    Topic topicA = new TopicImpl("topicA");
    project.addTopic(topicA);
    Topic topicB = new TopicImpl("topicB");
    project.addTopic(topicB);

    String topicC = "team.project.topicC";
    String topicI1 = "foo.my-internal.topic";
    String topicI2 = "_my-internal.topic";

    Set<String> appTopics =
        Arrays.asList(topicC, topicI1, topicI2).stream().collect(Collectors.toSet());
    when(adminClient.listApplicationTopics()).thenReturn(appTopics);

    topicManager.apply(topology, plan);
    plan.run();

    verify(adminClient, times(1)).createTopic(topicA, topicA.toString());
    verify(adminClient, times(1)).createTopic(topicB, topicB.toString());
    verify(adminClient, times(1)).deleteTopics(Collections.singletonList(topicC));
  }

  @Test
  public void topicDeleteWithConfiguredNoDelete() throws IOException {

    Properties props = new Properties();
    props.put(KAFKA_INTERNAL_TOPIC_PREFIXES, Arrays.asList("foo.", "_"));

    HashMap<String, String> cliOps = new HashMap<>();
    cliOps.put(BROKERS_OPTION, "");
    cliOps.put(ALLOW_DELETE_OPTION, "false");

    TopologyBuilderConfig config = new TopologyBuilderConfig(cliOps, props);

    TopicManager topicManager = new TopicManager(adminClient, schemaRegistryManager, config);

    // Topology after delete action
    Topology topology = new TopologyImpl();
    Project project = new ProjectImpl("project");
    topology.addProject(project);

    Topic topicA = new TopicImpl("topicA");
    project.addTopic(topicA);
    Topic topicB = new TopicImpl("topicB");
    project.addTopic(topicB);

    String topicC = "team.project.topicC";

    Set<String> appTopics = Arrays.asList(topicC).stream().collect(Collectors.toSet());
    when(adminClient.listApplicationTopics()).thenReturn(appTopics);

    topicManager.apply(topology, plan);
    plan.run();

    verify(adminClient, times(0)).deleteTopics(Collections.singletonList(topicC));
  }

  @Test
  public void topicDeleteWithConfiguredNoDeleteOnlyForTopics() throws IOException {

    Properties props = new Properties();
    props.put(ALLOW_DELETE_TOPICS, "true");

    HashMap<String, String> cliOps = new HashMap<>();
    cliOps.put(BROKERS_OPTION, "");
    cliOps.put(ALLOW_DELETE_OPTION, "false");

    TopologyBuilderConfig config = new TopologyBuilderConfig(cliOps, props);

    TopicManager topicManager = new TopicManager(adminClient, schemaRegistryManager, config);

    // Topology after delete action
    Topology topology = new TopologyImpl();
    Project project = new ProjectImpl("project");
    topology.addProject(project);

    Topic topicA = new TopicImpl("topicA");
    project.addTopic(topicA);
    Topic topicB = new TopicImpl("topicB");
    project.addTopic(topicB);

    String topicC = "team.project.topicC";

    Set<String> appTopics = Collections.singleton(topicC);
    when(adminClient.listApplicationTopics()).thenReturn(appTopics);

    topicManager.apply(topology, plan);
    plan.run();

    verify(adminClient, times(1)).deleteTopics(Collections.singletonList(topicC));
  }

  @Test
  public void topicDeleteWithConfiguredNoDeleteOnlyForTopicsAllDisabled() throws IOException {

    Properties props = new Properties();
    props.put(ALLOW_DELETE_TOPICS, "false");

    HashMap<String, String> cliOps = new HashMap<>();
    cliOps.put(BROKERS_OPTION, "");
    cliOps.put(ALLOW_DELETE_OPTION, "false");

    TopologyBuilderConfig config = new TopologyBuilderConfig(cliOps, props);

    TopicManager topicManager = new TopicManager(adminClient, schemaRegistryManager, config);

    // Topology after delete action
    Topology topology = new TopologyImpl();
    Project project = new ProjectImpl("project");
    topology.addProject(project);

    Topic topicA = new TopicImpl("topicA");
    project.addTopic(topicA);
    Topic topicB = new TopicImpl("topicB");
    project.addTopic(topicB);

    String topicC = "team.project.topicC";

    Set<String> appTopics = Collections.singleton(topicC);
    when(adminClient.listApplicationTopics()).thenReturn(appTopics);

    topicManager.apply(topology, plan);
    plan.run();

    verify(adminClient, times(0)).deleteTopics(Collections.singletonList(topicC));
  }

  @Test
  public void dryRunTest() throws IOException {

    plan = ExecutionPlan.init(backendController, outputStream);
    Project project = new ProjectImpl("project");
    Topic topicA = new TopicImpl("topicA");
    project.addTopic(topicA);
    Topic topicB = new TopicImpl("topicB");
    topicB.getConfig().put(NUM_PARTITIONS, "12");
    project.addTopic(topicB);
    Topology topology = new TopologyImpl();
    topology.addProject(project);

    Set<String> dummyTopicList = new HashSet<>();
    dummyTopicList.add(topicB.toString());
    when(adminClient.listApplicationTopics()).thenReturn(dummyTopicList);

    topicManager.apply(topology, plan);
    plan.run(true);

    verify(outputStream, times(2)).println(any(Action.class));
  }
}
